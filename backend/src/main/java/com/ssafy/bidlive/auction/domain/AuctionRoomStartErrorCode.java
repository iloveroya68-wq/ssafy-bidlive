package com.ssafy.bidlive.auction.domain;

/** 경매 방송 시작 오류 코드다. */
public enum AuctionRoomStartErrorCode {
	UNAUTHORIZED,
	NOT_AUCTION_SELLER,
	AUCTION_NOT_FOUND,
	AUCTION_START_NOT_ALLOWED,
	AUCTION_ALREADY_STARTED,
	AUCTION_MODERATION_BLOCKED
}
