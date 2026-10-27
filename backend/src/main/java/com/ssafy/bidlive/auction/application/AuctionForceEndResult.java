package com.ssafy.bidlive.auction.application;

import java.time.Instant;

/** 강제 종료 처리 결과. 응답 4필드와 환급 집계를 담는다. */
public record AuctionForceEndResult(
	long auctionId,
	Instant endedAt,
	int releasedHoldCount,
	long releasedHoldAmount,
	int refundedPredictionCount
) {
}
