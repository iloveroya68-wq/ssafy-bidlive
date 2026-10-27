package com.ssafy.bidlive.auction.application;

import com.ssafy.bidlive.auction.domain.ParticipatedAuctionHistoryCursor;

/** 참여 경매 이력 조회에 필요한 회원·커서·페이지 크기다. */
public record ParticipatedAuctionHistoryCriteria(
	long memberId,
	ParticipatedAuctionHistoryCursor cursor,
	int size
) {
}
