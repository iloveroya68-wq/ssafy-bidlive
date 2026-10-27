package com.ssafy.bidlive.deal.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.ssafy.bidlive.deal.domain.DealStatus;

/**
 * 에스크로 거래 전이의 영속성 계약이다. 메서드 하나가 전이 하나에 대응하며, 각 전이는
 * `deal_shipping` 갱신과 `deal.deal_status` 변경을 **한 단위로** 처리한다 — 둘 중 하나만 반영된
 * 상태는 어느 쪽 화면에서도 복구할 수 없다.
 */
public interface DealEscrowPersistence {

	/** 전이 판정에 필요한 사실을 읽고 그 거래 행을 잠근다. 같은 거래에 동시 요청이 오면 뒤가 기다린다. */
	Optional<DealEscrowTarget> lockDeal(long dealId);

	/** 배송지를 저장하고 발송 대기로 넘긴다. */
	void submitAddress(long dealId, ShippingAddressCommand address, Instant submittedAt);

	/** 발송 시각과 자동 확정 예정 시각을 기록하고 수령 대기로 넘긴다. */
	void confirmShipment(long dealId, Instant shippedAt, Instant autoConfirmAt);

	/**
	 * 수령 시각을 기록하고 거래를 완료(에스크로 해제)로 넘긴다.
	 *
	 * @param status 구매자가 직접 확인했으면 {@code COMPLETED}, 7일 자동 확정이면
	 *               {@code AUTO_COMPLETED}. 화면이 두 경우를 다른 문구로 구분한다
	 */
	void completeDeal(long dealId, Instant confirmedAt, DealStatus status);

	/**
	 * 자동 확정 기한이 지난 수령 대기 거래의 ID를 오래된 순으로 돌려준다.
	 *
	 * <p>여기서는 잠그지 않는다 — 잠금은 건별 트랜잭션이 {@link #lockDeal}로 다시 잡는다. 목록을
	 * 잠근 채로 지급까지 돌면 한 건이 실패할 때 전부 되돌아가고, 그사이 구매자의 직접 확인도 막힌다.
	 */
	List<Long> findAutoConfirmDueDealIds(Instant dueAt, int limit);

	/**
	 * 이 키로 이미 지급이 기록됐는지 본다.
	 *
	 * <p>거래 행을 잠그고 상태를 확인하므로 API 경로만 보면 중복 지급은 이미 막힌다. 이 검사는 7일
	 * 자동 확정 배치(DEAL-05 INTERNAL)가 같은 지급 경로를 쓸 때를 위한 것이다 — 명세가 요구하는
	 * 거래별 멱등 처리의 근거가 원장 유니크 키다.
	 */
	boolean payoutRecorded(String idempotencyKey);

	/** 에스크로 금액을 판매자 TRADE 지갑에 넣고 거래 포인트 원장에 남긴다. */
	void payoutToSeller(long sellerMemberId, long dealId, long amount, String idempotencyKey);
}
