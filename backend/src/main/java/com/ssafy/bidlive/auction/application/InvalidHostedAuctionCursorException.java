package com.ssafy.bidlive.auction.application;

/** 개최 경매 조회 커서가 해석할 수 없는 값일 때 발생한다. */
public class InvalidHostedAuctionCursorException extends RuntimeException {

	public InvalidHostedAuctionCursorException(Throwable cause) {
		super(cause);
	}
}
