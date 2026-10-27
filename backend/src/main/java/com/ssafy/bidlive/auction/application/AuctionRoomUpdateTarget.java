package com.ssafy.bidlive.auction.application;

import java.time.Instant;

/**
 * 수정 가능 여부 판정과 "유지" 계산에 필요한 경매방의 현재 값이다.
 *
 * <p>{@code status}가 문자열인 이유는 삭제 판정과 같다 — `auction_room.status`에 DB 제약이 없어
 * 예상 밖의 값이 들어 있을 수 있고, 필요한 판단은 "`WAITING`인가" 하나뿐이다.
 */
public record AuctionRoomUpdateTarget(
	Long auctionId,
	Long productId,
	Long sellerId,
	String status,
	String visibility,
	boolean deleted,
	long reservePrice,
	int durationMinutes,
	String startType,
	Instant scheduledAt
) {
}
