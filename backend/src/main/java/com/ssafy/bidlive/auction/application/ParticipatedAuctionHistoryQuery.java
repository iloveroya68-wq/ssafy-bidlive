package com.ssafy.bidlive.auction.application;

/** 회원 도메인에 사용자의 입찰·예측 참여 경매 이력을 제공하는 공개 조회 계약이다. */
public interface ParticipatedAuctionHistoryQuery {

	ParticipatedAuctionHistorySlice query(
		long memberId,
		String cursor,
		int size
	);
}
