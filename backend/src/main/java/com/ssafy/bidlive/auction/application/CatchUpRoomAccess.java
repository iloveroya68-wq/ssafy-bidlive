package com.ssafy.bidlive.auction.application;

import java.time.Instant;

/** 중간 입장 요약 접근 판정에 필요한 최소 경매방 정보다. */
public record CatchUpRoomAccess(
	long auctionId,
	long sellerId,
	String status,
	String visibility,
	boolean deleted,
	Instant endAt
) {
}
