package com.ssafy.bidlive.auction.domain;

import java.time.Instant;

/** 비크리 정산 순위 계산에 필요한 유효 입찰 사실이다. */
public record VickreyBid(
	long bidId,
	long memberId,
	long amount,
	Instant submittedAt
) {

	public VickreyBid {
		if (bidId <= 0) {
			throw new IllegalArgumentException("bidId must be positive");
		}
		if (memberId <= 0) {
			throw new IllegalArgumentException("memberId must be positive");
		}
		if (amount <= 0) {
			throw new IllegalArgumentException("amount must be positive");
		}
		if (submittedAt == null) {
			throw new IllegalArgumentException("submittedAt must not be null");
		}
	}
}
