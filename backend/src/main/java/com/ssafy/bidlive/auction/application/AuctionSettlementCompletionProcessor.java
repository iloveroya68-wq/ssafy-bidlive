package com.ssafy.bidlive.auction.application;

/** DB 커밋이 끝난 경매의 Redis 상태를 ENDED로 닫는 포트다. */
public interface AuctionSettlementCompletionProcessor {

	AuctionSettlementCompletionResult complete(
		AuctionSettlementCompletionCommand command
	);
}
