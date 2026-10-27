package com.ssafy.bidlive.auction.application;

/** 회원 도메인에 사용자가 낙찰 받은 경매 이력을 제공하는 공개 조회 계약이다. */
public interface WonAuctionHistoryQuery {

	WonAuctionHistorySlice query(long memberId, String cursor, int size);
}
