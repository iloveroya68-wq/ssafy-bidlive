package com.ssafy.bidlive.auction.application;

import com.ssafy.bidlive.auction.domain.AuctionRoomStartErrorCode;

/** 방송을 시작할 수 없을 때 API 계층으로 오류 코드를 전달한다. */
public class AuctionRoomStartException extends RuntimeException {

	private final AuctionRoomStartErrorCode errorCode;

	public AuctionRoomStartException(AuctionRoomStartErrorCode errorCode) {
		super(errorCode.name());
		this.errorCode = errorCode;
	}

	public AuctionRoomStartErrorCode getErrorCode() {
		return errorCode;
	}
}
