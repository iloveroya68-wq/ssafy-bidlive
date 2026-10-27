package com.ssafy.bidlive.auction.api.dto;

import java.time.OffsetDateTime;

import com.ssafy.bidlive.auction.application.AuctionRoomCreationResult;
import com.ssafy.bidlive.auction.domain.AuctionStartType;
import com.ssafy.bidlive.auction.domain.AuctionStatus;

public record AuctionRoomCreationResponse(
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

	public static AuctionRoomCreationResponse from(
		AuctionRoomCreationResult result
	) {
		return new AuctionRoomCreationResponse(
			result.auctionId(),
			result.productId(),
			result.sellerId(),
			result.status(),
			result.reservePrice(),
			result.durationMinutes(),
			result.startType(),
			result.scheduledAt(),
			result.createdAt()
		);
	}
}
