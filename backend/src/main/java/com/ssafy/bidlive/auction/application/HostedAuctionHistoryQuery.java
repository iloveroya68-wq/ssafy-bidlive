package com.ssafy.bidlive.auction.application;

/** 회원 도메인에 판매자가 개최한 경매 이력을 제공하는 공개 조회 계약이다. */
public interface HostedAuctionHistoryQuery {

	HostedAuctionHistorySlice query(long sellerId, String cursor, int size);
}
