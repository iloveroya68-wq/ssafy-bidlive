package com.ssafy.bidlive.auction.application;

import com.ssafy.bidlive.auction.domain.AuctionRoomJoinErrorCode;

/** 대기방에 입장할 수 없을 때 API 계층으로 오류 코드를 전달한다. */
public class AuctionRoomJoinException extends RuntimeException {

	private final AuctionRoomJoinErrorCode errorCode;

	public AuctionRoomJoinException(AuctionRoomJoinErrorCode errorCode) {
		super(errorCode.name());
		this.errorCode = errorCode;
	}

	public AuctionRoomJoinErrorCode getErrorCode() {
		return errorCode;
	}
}
