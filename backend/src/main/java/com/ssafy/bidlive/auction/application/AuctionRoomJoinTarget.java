package com.ssafy.bidlive.auction.application;

import java.time.Instant;

/** 입장 가능 여부 판정과 응답 구성에 필요한 경매방의 현재 값이다. */
public record AuctionRoomJoinTarget(
	Long auctionId,
	Long sellerId,
	String status,
	String visibility,
	boolean deleted,
	Instant scheduledAt
) {
}
