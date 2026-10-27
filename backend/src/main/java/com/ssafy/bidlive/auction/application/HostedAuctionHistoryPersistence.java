package com.ssafy.bidlive.auction.application;

/** 판매자 기준 경매 이력을 PostgreSQL에서 조회하는 영속성 계약이다. */
public interface HostedAuctionHistoryPersistence {

	HostedAuctionHistoryPage find(HostedAuctionHistoryCriteria criteria);
}
