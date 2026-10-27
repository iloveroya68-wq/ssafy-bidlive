package com.ssafy.bidlive.auction.domain;

/** 경매방 삭제 오류 코드다. */
public enum AuctionRoomDeleteErrorCode {
	UNAUTHORIZED,
	NOT_AUCTION_SELLER,
	AUCTION_NOT_FOUND,
	AUCTION_ALREADY_STARTED,
	AUCTION_HAS_PARTICIPATION
}
