package com.ssafy.bidlive.auction.application;

import com.ssafy.bidlive.auction.domain.WonAuctionHistoryCursor;

/** 낙찰 경매 이력 조회에 필요한 회원·커서·페이지 크기다. */
public record WonAuctionHistoryCriteria(
	long memberId,
	WonAuctionHistoryCursor cursor,
	int size
) {
}
