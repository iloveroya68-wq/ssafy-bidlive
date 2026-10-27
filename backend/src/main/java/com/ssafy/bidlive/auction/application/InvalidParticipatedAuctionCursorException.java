package com.ssafy.bidlive.auction.application;

/** 참여 경매 조회 커서가 해석할 수 없는 값일 때 발생한다. */
public class InvalidParticipatedAuctionCursorException
	extends RuntimeException {

	public InvalidParticipatedAuctionCursorException(Throwable cause) {
		super(cause);
	}
}
