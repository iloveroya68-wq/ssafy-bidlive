package com.ssafy.bidlive.auction.application;

import java.time.Instant;

/** 판매자 방송 연장 후 외부 응답과 이벤트에 사용할 확정 상태다. */
public record AuctionExtensionResult(
	long auctionId,
	Instant previousEndAt,
	Instant endAt,
	int extensionCount,
	int remainingExtensions
) {
}
