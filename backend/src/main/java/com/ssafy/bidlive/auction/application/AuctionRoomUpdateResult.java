package com.ssafy.bidlive.auction.application;

import java.time.Instant;

import com.ssafy.bidlive.auction.domain.AuctionStartType;

/** 수정 후의 경매방 전체 값이다. 부분 수정이어도 응답은 확정된 전체 값을 준다. */
public record AuctionRoomUpdateResult(
	Long auctionId,
	Long productId,
	String status,
	String title,
	String description,
	String categoryCode,
	String imageUrl,
	long reservePrice,
	int durationMinutes,
	AuctionStartType startType,
	Instant scheduledAt,
	Instant updatedAt
) {
}
