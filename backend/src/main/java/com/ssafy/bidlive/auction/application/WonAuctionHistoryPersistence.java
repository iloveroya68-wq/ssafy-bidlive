package com.ssafy.bidlive.auction.application;

/** 회원이 낙찰 받은 경매 이력을 PostgreSQL에서 조회하는 영속성 계약이다. */
public interface WonAuctionHistoryPersistence {

	WonAuctionHistoryPage find(WonAuctionHistoryCriteria criteria);
}
