package com.ssafy.bidlive.bid.api;

/** 입찰 요청에서 유효한 인증 회원 식별자를 얻지 못했음을 나타낸다. */
class BidUnauthenticatedException extends RuntimeException {

	BidUnauthenticatedException() {
		super("Authentication is required");
	}

	BidUnauthenticatedException(Throwable cause) {
		super("Authentication is invalid", cause);
	}
}
