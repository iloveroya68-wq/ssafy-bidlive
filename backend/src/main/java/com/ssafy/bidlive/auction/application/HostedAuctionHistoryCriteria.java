package com.ssafy.bidlive.auction.application;

import com.ssafy.bidlive.auction.domain.HostedAuctionHistoryCursor;

/** 개최 경매 이력 영속성 조회에 필요한 판매자·커서·페이지 크기다. */
public record HostedAuctionHistoryCriteria(
	long sellerId,
	HostedAuctionHistoryCursor cursor,
	int size
) {
}
