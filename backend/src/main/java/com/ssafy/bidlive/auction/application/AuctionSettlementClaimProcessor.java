package com.ssafy.bidlive.auction.application;

/** 마감된 LIVE 경매의 정산 실행권을 Redis에서 원자적으로 선점하는 계약이다. */
public interface AuctionSettlementClaimProcessor {

	AuctionSettlementClaimResult claim(
		AuctionSettlementClaimCommand command
	);
}
