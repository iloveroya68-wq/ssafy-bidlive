package com.ssafy.bidlive.deal.domain;

/**
 * 낙찰 이후 에스크로 거래의 상태다. 전이는 한 방향으로만 흐른다.
 *
 * <pre>
 * WAITING_ADDRESS ──구매자 배송지 입력──▶ WAITING_SHIPMENT
 *                 ──판매자 발송 확인──▶ WAITING_RECEIPT
 *                 ──구매자 수령 확인──▶ COMPLETED
 * </pre>
 *
 * <p>{@code deal.deal_status} 컬럼에는 DB 제약이 없어 여기 없는 값이 들어 있을 수 있다. 그래서
 * 판정은 문자열 비교로 하고(=예상 밖의 값이 500을 만들지 않는다) 이 enum은 **기대하는 값의 단일
 * 출처**로만 쓴다.
 *
 * <p>{@code AUTO_COMPLETED}는 7일 자동 구매 확정(DEAL-05 INTERNAL)이 쓸 값이다. 그 배치는 아직
 * 없지만 「내 거래 할 일」 명세가 제외 목록에 이름을 적어 두었으므로 값도 여기에 둔다.
 */
public enum DealStatus {
	WAITING_ADDRESS,
	WAITING_SHIPMENT,
	WAITING_RECEIPT,
	COMPLETED,
	AUTO_COMPLETED,
	CANCELLED
}
