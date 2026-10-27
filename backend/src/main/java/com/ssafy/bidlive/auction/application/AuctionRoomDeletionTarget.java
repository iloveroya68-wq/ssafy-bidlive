package com.ssafy.bidlive.auction.application;

/**
 * 삭제 가능 여부를 판정하는 데 필요한 경매방 사실들이다. 한 번의 조회로 모아 오고 판정은 유스케이스가 한다.
 *
 * <p>{@code status}를 문자열로 두는 이유: `auction_room.status`에는 DB 제약이 없어 예상 밖의 값이
 * 들어 있을 수 있다. enum으로 변환하면 그 값 하나 때문에 500이 나가는데, 삭제 판정에 필요한 것은
 * "`WAITING`인가" 뿐이라 문자열 비교로 충분하다.
 */
public record AuctionRoomDeletionTarget(
	Long auctionId,
	Long sellerId,
	String status,
	String visibility,
	boolean deleted,
	boolean hasParticipation
) {
}
