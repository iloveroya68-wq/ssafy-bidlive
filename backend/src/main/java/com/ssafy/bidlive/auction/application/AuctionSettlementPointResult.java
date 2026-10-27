package com.ssafy.bidlive.auction.application;

/** 비크리 낙찰가 차감과 입찰 홀드 해제 결과를 요약한다. */
public record AuctionSettlementPointResult(
	long auctionId,
	long settledAmount,
	long releasedAmount,
	int settlementOperationCount,
	int releaseOperationCount
) {
}
