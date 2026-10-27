package com.ssafy.bidlive.auction.application;

import com.ssafy.bidlive.auction.domain.AuctionRoomUpdateErrorCode;

/** 경매방을 수정할 수 없을 때 API 계층으로 오류 코드를 전달한다. */
public class AuctionRoomUpdateException extends RuntimeException {

	private final AuctionRoomUpdateErrorCode errorCode;

	public AuctionRoomUpdateException(AuctionRoomUpdateErrorCode errorCode) {
		super(errorCode.name());
		this.errorCode = errorCode;
	}

	public AuctionRoomUpdateException(AuctionRoomUpdateErrorCode errorCode, Throwable cause) {
		super(errorCode.name(), cause);
		this.errorCode = errorCode;
	}

	public AuctionRoomUpdateErrorCode getErrorCode() {
		return errorCode;
	}
}
