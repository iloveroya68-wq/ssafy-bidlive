package com.ssafy.bidlive.auction.application;

/** 낙찰 경매 조회 커서가 해석할 수 없는 값일 때 발생한다. */
public class InvalidWonAuctionCursorException extends RuntimeException {

	public InvalidWonAuctionCursorException(Throwable cause) {
		super(cause);
	}
}
