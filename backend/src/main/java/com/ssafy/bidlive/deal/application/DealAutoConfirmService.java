package com.ssafy.bidlive.deal.application;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import com.ssafy.bidlive.deal.domain.DealStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 발송 후 7일이 지난 미확정 거래를 자동 완료하고 에스크로 대금을 판매자에게 지급한다
 * (DEAL-05 INTERNAL).
 *
 * <p><b>구매자 수령 확인과 같은 지급 경로를 쓴다</b> — 명세가 요구한 것이고, 두 경로가 갈리면 한쪽만
 * 고치는 사고가 난다. 다른 점은 완료 상태뿐이다({@code AUTO_COMPLETED}).
 *
 * <p>대상 조회와 건별 처리를 나눈 이유: 목록을 잠근 채로 지급까지 돌면 한 건이 실패할 때 전부
 * 되돌아가고, 그사이 구매자의 직접 확인도 막힌다. 그래서 조회는 잠금 없이 하고 건별 트랜잭션이
 * 다시 잠근 뒤 상태를 **재확인**한다 — 조회와 처리 사이에 구매자가 직접 확인했으면 건너뛴다.
 *
 * <p>중복 지급은 원장 멱등 키가 막는다. 두 인스턴스가 같은 거래를 동시에 집어도 지급은 한 번이다.
 */
@Service
public class DealAutoConfirmService {

	/** 자동 확정 시각도 다른 거래 처리와 같은 서비스 기준 오프셋을 쓴다. */
	private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

	/** 거래당 한 번만 지급되게 하는 원장 키. 구매자 수령 확인과 **같은 키**여야 한다. */
	private static final String PAYOUT_KEY_PREFIX = "DEAL_COMPLETION_PAYOUT:";

	private final DealEscrowPersistence escrowPersistence;
	private final Clock clock;

	@Autowired
	public DealAutoConfirmService(DealEscrowPersistence escrowPersistence) {
		this(escrowPersistence, Clock.system(SERVICE_ZONE));
	}

	/** 확정 시각을 고정해야 하는 테스트에서만 사용한다. */
	DealAutoConfirmService(DealEscrowPersistence escrowPersistence, Clock clock) {
		this.escrowPersistence = escrowPersistence;
		this.clock = clock;
	}

	/** 지금 기준으로 자동 확정 기한이 지난 거래 ID를 오래된 순으로 최대 {@code limit}건 돌려준다. */
	@Transactional(readOnly = true)
	public List<Long> findDueDealIds(int limit) {
		return escrowPersistence.findAutoConfirmDueDealIds(clock.instant(), limit);
	}

	/**
	 * 한 건을 자동 확정한다. 거래별 트랜잭션이라 한 건이 실패해도 나머지는 그대로 진행된다.
	 *
	 * @return 실제로 확정했으면 {@code true}. 그사이 상태가 바뀌었거나 거래가 사라졌으면 {@code false}
	 */
	@Transactional
	public boolean confirmDueDeal(long dealId) {
		DealEscrowTarget deal = escrowPersistence.lockDeal(dealId).orElse(null);
		// 조회와 처리 사이에 구매자가 직접 확인했거나 거래가 취소됐을 수 있다 — 그건 정상이고 건너뛴다
		if (deal == null || !DealStatus.WAITING_RECEIPT.name().equals(deal.status())) {
			return false;
		}

		String idempotencyKey = PAYOUT_KEY_PREFIX + dealId;
		// 0원 지급은 건너뛴다 — 원장이 증감 0인 행을 거부한다(수령 확인과 같은 판단)
		if (deal.escrowAmount() > 0 && !escrowPersistence.payoutRecorded(idempotencyKey)) {
			escrowPersistence.payoutToSeller(
				deal.sellerMemberId(),
				dealId,
				deal.escrowAmount(),
				idempotencyKey
			);
		}

		Instant now = clock.instant();
		escrowPersistence.completeDeal(dealId, now, DealStatus.AUTO_COMPLETED);
		return true;
	}
}
