package com.ssafy.bidlive.auction.domain;

import java.time.Instant;

/** Redis와 PostgreSQL에 반영된 연장을 브로드캐스트하고 정산 예약에 적용하는 사실이다. */
public record AuctionExtendedEvent(
	long auctionId,
	Instant endAt,
	int extensionCount,
	int remainingExtensions
) {

	public AuctionExtendedEvent {
		if (auctionId <= 0) {
			throw new IllegalArgumentException("auctionId must be positive");
		}
		if (endAt == null) {
			throw new IllegalArgumentException("endAt must not be null");
		}
		if (extensionCount < 1) {
			throw new IllegalArgumentException(
				"extensionCount must be positive"
			);
		}
		if (remainingExtensions < 0) {
			throw new IllegalArgumentException(
				"remainingExtensions must not be negative"
			);
		}
	}
}
