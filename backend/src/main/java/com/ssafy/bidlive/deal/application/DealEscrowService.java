package com.ssafy.bidlive.deal.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import com.ssafy.bidlive.deal.domain.DealErrorCode;
import com.ssafy.bidlive.deal.domain.DealStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 낙찰 이후 에스크로 거래를 다음 단계로 넘긴다(DEAL-05).
 *
 * <p>세 전이의 판정 순서가 계약이다: 없음(404) → 주체 아님(403) → 상태 아님(409). 상태를 권한보다
 * 뒤에 보는 이유는 남의 거래가 지금 어느 단계인지 알려주지 않기 위해서다.
 *
 * <p>모든 전이는 거래 행을 잠그고 시작한다. 잠그지 않으면 같은 거래에 두 요청이 동시에 들어와 둘 다
 * 상태 검사를 통과할 수 있고, 수령 확인에서는 그게 **에스크로 이중 지급**이 된다.
 */
@Service
public class DealEscrowService {

	/** 거래 시각도 다른 도메인과 같은 서비스 기준 오프셋을 쓴다. */
	private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

	/** 발송 확인 시점에 잡아 두는 자동 구매 확정 대기 기간(DEAL-05 「7일 자동 구매 확정」). */
	private static final Duration AUTO_CONFIRM_AFTER = Duration.ofDays(7);

	/** 거래당 한 번만 지급되게 하는 원장 키. 자동 확정 배치도 같은 키를 써야 한다. */
	private static final String PAYOUT_KEY_PREFIX = "DEAL_COMPLETION_PAYOUT:";

	private final DealEscrowPersistence escrowPersistence;
	private final Clock clock;

	@Autowired
	public DealEscrowService(DealEscrowPersistence escrowPersistence) {
		this(escrowPersistence, Clock.system(SERVICE_ZONE));
	}

	/** 전이 시각을 고정해야 하는 테스트에서만 사용한다. */
	DealEscrowService(DealEscrowPersistence escrowPersistence, Clock clock) {
		this.escrowPersistence = escrowPersistence;
		this.clock = clock;
	}

	/** 구매자가 배송지를 입력해 발송 대기로 넘긴다. */
	@Transactional
	public DealTransitionResults.Address submitShippingAddress(
		long dealId,
		Long memberId,
		ShippingAddressCommand command
	) {
		if (command == null) {
			throw new DealException(DealErrorCode.INVALID_SHIPPING_ADDRESS);
		}
		// 값 검증을 잠금보다 먼저 한다 — 잘못된 요청 때문에 남의 요청이 대기할 이유가 없다
		ShippingAddressCommand address = command.normalized();

		DealEscrowTarget deal = lockDeal(dealId, memberId);
		requireBuyer(deal, memberId);
		requireStatus(deal, DealStatus.WAITING_ADDRESS);

		Instant now = clock.instant();
		escrowPersistence.submitAddress(dealId, address, now);
		return new DealTransitionResults.Address(dealId, DealStatus.WAITING_SHIPMENT, now);
	}

	/** 판매자가 발송을 확인해 수령 대기로 넘긴다. */
	@Transactional
	public DealTransitionResults.Shipment confirmShipment(long dealId, Long memberId) {
		DealEscrowTarget deal = lockDeal(dealId, memberId);
		requireSeller(deal, memberId);
		requireStatus(deal, DealStatus.WAITING_SHIPMENT);

		Instant now = clock.instant();
		Instant autoConfirmAt = now.plus(AUTO_CONFIRM_AFTER);
		escrowPersistence.confirmShipment(dealId, now, autoConfirmAt);
		return new DealTransitionResults.Shipment(
			dealId,
			DealStatus.WAITING_RECEIPT,
			now,
			autoConfirmAt
		);
	}

	/**
	 * 구매자가 수령을 확인해 에스크로 대금을 판매자에게 지급하고 거래를 완료한다.
	 *
	 * <p>지급과 완료 표시가 한 트랜잭션이다. 지급만 되고 상태가 남으면 다음 요청이 또 지급하고,
	 * 상태만 바뀌면 판매자 돈이 사라진다.
	 *
	 * <p>구매자 쪽 보류금 해제는 여기서 하지 않는다. 입찰 시 보류(PNT-03)가 아직 없어 풀 홀드가
	 * 없고, 없는 홀드를 차감하면 `ck_wallet_held_balance`가 막는다.
	 * ponytail: 판매자 지급만 기록한다. 입찰 보류가 들어오면 같은 트랜잭션에 구매자 홀드 해제 원장을
	 * 한 줄 더 넣는다.
	 */
	@Transactional
	public DealTransitionResults.Receipt confirmReceipt(long dealId, Long memberId) {
		DealEscrowTarget deal = lockDeal(dealId, memberId);
		requireBuyer(deal, memberId);
		requireStatus(deal, DealStatus.WAITING_RECEIPT);

		String idempotencyKey = PAYOUT_KEY_PREFIX + dealId;
		// 0원 지급은 건너뛴다 — `escrow_amount`는 0을 허용하는데 원장은 증감 0인 행을 거부한다
		// (`ck_trade_ledger_nonzero_delta`). 완료 처리 자체는 그대로 진행한다.
		if (deal.escrowAmount() > 0 && !escrowPersistence.payoutRecorded(idempotencyKey)) {
			escrowPersistence.payoutToSeller(
				deal.sellerMemberId(),
				dealId,
				deal.escrowAmount(),
				idempotencyKey
			);
		}

		Instant now = clock.instant();
		escrowPersistence.completeDeal(dealId, now, DealStatus.COMPLETED);
		return new DealTransitionResults.Receipt(
			dealId,
			DealStatus.COMPLETED,
			deal.escrowAmount(),
			now
		);
	}

	private DealEscrowTarget lockDeal(long dealId, Long memberId) {
		if (memberId == null) {
			throw new DealException(DealErrorCode.UNAUTHORIZED);
		}
		return escrowPersistence.lockDeal(dealId)
			.orElseThrow(() -> new DealException(DealErrorCode.DEAL_NOT_FOUND));
	}

	private static void requireBuyer(DealEscrowTarget deal, Long memberId) {
		if (!memberId.equals(deal.buyerMemberId())) {
			throw new DealException(DealErrorCode.NOT_DEAL_BUYER);
		}
	}

	private static void requireSeller(DealEscrowTarget deal, Long memberId) {
		if (!memberId.equals(deal.sellerMemberId())) {
			throw new DealException(DealErrorCode.NOT_DEAL_SELLER);
		}
	}

	private static void requireStatus(DealEscrowTarget deal, DealStatus expected) {
		if (!expected.name().equals(deal.status())) {
			throw new DealException(DealErrorCode.INVALID_DEAL_STATUS);
		}
	}
}
