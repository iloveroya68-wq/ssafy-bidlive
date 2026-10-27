package com.ssafy.bidlive.auction.application;

/**
 * 방송 시작 가능 여부 판정에 필요한 경매방의 현재 값이다.
 *
 * <p>{@code moderationStatus}는 상품 검열 결과다 — 명세가 "검열 결과가 방송 시작을 차단하는
 * 상태라면 시작할 수 없다"고 정한 유일한 게이트다(422).
 */
public record AuctionRoomStartTarget(
	Long auctionId,
	Long sellerId,
	String status,
	String visibility,
	boolean deleted,
	long reservePrice,
	int durationMinutes,
	String moderationStatus
) {
}
