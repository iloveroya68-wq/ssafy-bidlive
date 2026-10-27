package com.ssafy.bidlive.auction.application;

import java.time.Instant;

/** Redis 정산 선점에 필요한 경매 식별자와 서버 기준 요청 시각을 전달한다. */
public record AuctionSettlementClaimCommand(
	long auctionId,
	Instant requestedAt
) {

	public AuctionSettlementClaimCommand {
		if (auctionId <= 0) {
			throw new IllegalArgumentException("auctionId must be positive");
		}
		if (requestedAt == null) {
			throw new IllegalArgumentException("requestedAt must not be null");
		}
	}
}
