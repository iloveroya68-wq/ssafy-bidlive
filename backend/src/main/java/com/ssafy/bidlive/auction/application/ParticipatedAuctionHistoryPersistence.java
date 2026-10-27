package com.ssafy.bidlive.auction.application;

/** 회원의 입찰·예측 참여 경매를 PostgreSQL에서 조회하는 영속성 계약이다. */
public interface ParticipatedAuctionHistoryPersistence {

	ParticipatedAuctionHistoryPage find(
		ParticipatedAuctionHistoryCriteria criteria
	);
}
