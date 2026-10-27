package com.ssafy.bidlive.auction.application;

import com.ssafy.bidlive.auction.domain.AuctionRoomDetailErrorCode;

/** 경매방 상세 조회 중 발생한 계약상 실패 코드와 원인을 전달합니다. */
public class AuctionRoomDetailException extends RuntimeException {

	private final AuctionRoomDetailErrorCode errorCode;

	public AuctionRoomDetailException(AuctionRoomDetailErrorCode errorCode) {
		this(errorCode, null);
	}

	public AuctionRoomDetailException(
		AuctionRoomDetailErrorCode errorCode,
		Throwable cause
	) {
		super(errorCode.name(), cause);
		this.errorCode = errorCode;
	}

	public AuctionRoomDetailErrorCode getErrorCode() {
		return errorCode;
	}
}
