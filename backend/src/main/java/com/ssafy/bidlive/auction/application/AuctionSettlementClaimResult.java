package com.ssafy.bidlive.auction.application;

/** Redis 정산 선점의 성공 또는 선점하지 않은 단일 사유다. */
public enum AuctionSettlementClaimResult {
	CLAIMED,
	AUCTION_NOT_FOUND,
	AUCTION_NOT_DUE,
	AUCTION_NOT_LIVE,
	ALREADY_SETTLING,
	ALREADY_ENDED;

	public boolean claimed() {
		return this == CLAIMED;
	}
}
