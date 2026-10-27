package com.ssafy.bidlive.auction.domain;

/** 경매방 상세 조회 실패를 외부 API 오류 코드로 구분합니다. */
public enum AuctionRoomDetailErrorCode {
	INVALID_AUCTION_ID,
	AUCTION_NOT_FOUND,
	AUCTION_DELETED
}
