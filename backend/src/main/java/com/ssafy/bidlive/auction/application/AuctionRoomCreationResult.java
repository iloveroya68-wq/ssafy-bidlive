package com.ssafy.bidlive.auction.application;

import java.time.OffsetDateTime;

import com.ssafy.bidlive.auction.domain.AuctionStartType;
import com.ssafy.bidlive.auction.domain.AuctionStatus;

public record AuctionRoomCreationResult(
	Long auctionId,
	Long productId,
	Long sellerId,
	AuctionStatus status,
	long reservePrice,
	int durationMinutes,
	AuctionStartType startType,
	OffsetDateTime scheduledAt,
	OffsetDateTime createdAt
) {
}
