package com.ssafy.bidlive.auction.domain;

/** 경매방 수정 오류 코드다. */
public enum AuctionRoomUpdateErrorCode {
	EMPTY_UPDATE_REQUEST,
	INVALID_AUCTION_REQUEST,
	UNAUTHORIZED,
	NOT_AUCTION_SELLER,
	AUCTION_NOT_FOUND,
	AUCTION_UPDATE_NOT_ALLOWED
}
