package com.ssafy.bidlive.auction.application;

import java.time.Instant;

import com.ssafy.bidlive.auction.domain.VickreyPriceDecisionType;

/** BID-07 전체 정산의 실행 상태와 공개 가능한 비크리 결과다. */
public record AuctionSettlementResult(
	long auctionId,
	AuctionSettlementStatus status,
	boolean newlySettled,
	Long winnerId,
	String winner,
	Long highestBidAmount,
	Long secondHighestBidAmount,
	Long reservePrice,
	Long finalPrice,
	VickreyPriceDecisionType priceDecisionType,
	Instant settledAt
) {

	public boolean completedNormally() {
		return status == AuctionSettlementStatus.SETTLED
			|| status == AuctionSettlementStatus.NO_SALE;
	}

	public static AuctionSettlementResult skipped(
		long auctionId,
		AuctionSettlementStatus status
	) {
		return new AuctionSettlementResult(
			auctionId,
			status,
			false,
			null,
			null,
			null,
			null,
			null,
			null,
			null,
			null
		);
	}
}
