package com.ssafy.bidlive.auction.application;

import com.ssafy.bidlive.auction.domain.AuctionRoomListErrorCode;

/** 목록 조회 조건이 유효하지 않을 때 API 계층으로 오류 코드를 전달한다. */
public class AuctionRoomListException extends RuntimeException {

	private final AuctionRoomListErrorCode errorCode;

	public AuctionRoomListException(AuctionRoomListErrorCode errorCode) {
		super(errorCode.name());
		this.errorCode = errorCode;
	}

	public AuctionRoomListException(AuctionRoomListErrorCode errorCode, Throwable cause) {
		super(errorCode.name(), cause);
		this.errorCode = errorCode;
	}

	public AuctionRoomListErrorCode getErrorCode() {
		return errorCode;
	}
}
