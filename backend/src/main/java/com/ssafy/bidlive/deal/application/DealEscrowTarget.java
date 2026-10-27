package com.ssafy.bidlive.deal.application;

/**
 * 전이 가능 여부를 판정하는 데 필요한 거래 사실들이다. 행을 잠근 채 한 번에 읽어 오고 판정은
 * 유스케이스가 한다.
 *
 * <p>{@code status}를 문자열로 두는 이유는 `auction_room.status`와 같다 — `deal.deal_status`에
 * DB 제약이 없어 enum으로 변환하면 예상 밖의 값 하나가 500을 만든다. 판정에 필요한 것은 "기대하는
 * 상태인가" 뿐이라 문자열 비교로 충분하다.
 */
public record DealEscrowTarget(
	long dealId,
	Long buyerMemberId,
	Long sellerMemberId,
	String status,
	long escrowAmount
) {
}
