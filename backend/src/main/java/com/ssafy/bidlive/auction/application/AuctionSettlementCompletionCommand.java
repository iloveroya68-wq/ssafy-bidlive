package com.ssafy.bidlive.auction.application;

import java.time.Instant;

/** 커밋된 정산을 Redis 실시간 상태에 반영할 때 필요한 값이다. */
public record AuctionSettlementCompletionCommand(
	long auctionId,
	Instant completedAt
) {

	public AuctionSettlementCompletionCommand {
		if (auctionId <= 0) {
			throw new IllegalArgumentException(
				"auctionId must be positive"
			);
		}
		if (completedAt == null) {
			throw new IllegalArgumentException(
				"completedAt must not be null"
			);
		}
	}
}
