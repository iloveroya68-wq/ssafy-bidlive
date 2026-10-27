package com.ssafy.bidlive.auction.application;

/** Redis 정산 완료 전이의 성공 또는 실패 사유다. */
public enum AuctionSettlementCompletionResult {
	COMPLETED,
	ALREADY_ENDED,
	AUCTION_NOT_FOUND,
	AUCTION_NOT_SETTLING;

	public boolean completed() {
		return this == COMPLETED || this == ALREADY_ENDED;
	}
}
