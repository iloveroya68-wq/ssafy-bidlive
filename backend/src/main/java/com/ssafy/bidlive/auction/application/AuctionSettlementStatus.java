package com.ssafy.bidlive.auction.application;

/** 전체 정산 호출이 완료되거나 실행하지 않은 최종 사유다. */
public enum AuctionSettlementStatus {
	SETTLED,
	NO_SALE,
	ALREADY_COMPLETED,
	SKIPPED_NOT_FOUND,
	SKIPPED_NOT_DUE,
	SKIPPED_NOT_LIVE,
	SKIPPED_REDIS_NOT_FOUND
}
