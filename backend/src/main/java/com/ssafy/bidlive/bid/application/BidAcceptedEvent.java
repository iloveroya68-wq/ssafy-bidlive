package com.ssafy.bidlive.bid.application;

/** 승인된 입찰의 경매 식별자와 공개 집계값을 후속 처리에 전달한다. */
public record BidAcceptedEvent(
	long auctionId,
	long bidCount,
	long participantCount
) {

	public BidAcceptedEvent {
		if (auctionId <= 0L) {
			throw new IllegalArgumentException("auctionId must be positive");
		}
		if (bidCount < 0L) {
			throw new IllegalArgumentException("bidCount must not be negative");
		}
		if (participantCount < 0L) {
			throw new IllegalArgumentException(
				"participantCount must not be negative"
			);
		}
	}
}
