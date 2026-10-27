package com.ssafy.bidlive.auction.application;

import java.time.Instant;

/** Redis 원자 연장 처리에 필요한 경매·판매자·서버 시각을 전달한다. */
public record AuctionExtensionCommand(
	long auctionId,
	long requesterId,
	Instant requestedAt
) {

	public AuctionExtensionCommand {
		if (auctionId <= 0) {
			throw new IllegalArgumentException("auctionId must be positive");
		}
		if (requesterId <= 0) {
			throw new IllegalArgumentException("requesterId must be positive");
		}
		if (requestedAt == null) {
			throw new IllegalArgumentException("requestedAt must not be null");
		}
	}
}
