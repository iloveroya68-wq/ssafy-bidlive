package com.ssafy.bidlive.auction.application;

import com.ssafy.bidlive.auction.domain.AuctionRoomDeleteErrorCode;

/** 경매방을 삭제할 수 없을 때 API 계층으로 오류 코드를 전달한다. */
public class AuctionRoomDeleteException extends RuntimeException {

	private final AuctionRoomDeleteErrorCode errorCode;

	public AuctionRoomDeleteException(AuctionRoomDeleteErrorCode errorCode) {
		super(errorCode.name());
		this.errorCode = errorCode;
	}

	public AuctionRoomDeleteErrorCode getErrorCode() {
		return errorCode;
	}
}
