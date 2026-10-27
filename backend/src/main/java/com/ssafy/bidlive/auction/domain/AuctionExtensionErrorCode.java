package com.ssafy.bidlive.auction.domain;

/** 판매자 권한과 방송 연장 가능 조건의 거부 사유다. */
public enum AuctionExtensionErrorCode {
	UNAUTHORIZED,
	AUCTION_NOT_FOUND,
	NOT_AUCTION_SELLER,
	AUCTION_NOT_LIVE,
	EXTENSION_LIMIT_EXCEEDED,
	EXTENSION_TOO_LATE
}
