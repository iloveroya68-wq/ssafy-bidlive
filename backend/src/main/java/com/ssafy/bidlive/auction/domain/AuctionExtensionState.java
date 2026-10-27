package com.ssafy.bidlive.auction.domain;

import java.time.Instant;

/** Redis 경매 상태 중 판매자 방송 연장 판정에 필요한 값만 표현한다. */
public record AuctionExtensionState(
	long sellerId,
	AuctionStatus status,
	Instant endAt,
	int extensionCount
) {

	public AuctionExtensionState {
		if (sellerId <= 0) {
			throw new IllegalArgumentException("sellerId must be positive");
		}
		if (status == null) {
			throw new IllegalArgumentException("status must not be null");
		}
		if (endAt == null) {
			throw new IllegalArgumentException("endAt must not be null");
		}
		if (extensionCount < 0) {
			throw new IllegalArgumentException(
				"extensionCount must not be negative"
			);
		}
	}
}
