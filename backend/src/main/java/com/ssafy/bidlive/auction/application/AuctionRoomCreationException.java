package com.ssafy.bidlive.auction.application;

import com.ssafy.bidlive.auction.domain.AuctionRoomCreationErrorCode;

public class AuctionRoomCreationException extends RuntimeException {

	private final AuctionRoomCreationErrorCode errorCode;

	public AuctionRoomCreationException(AuctionRoomCreationErrorCode errorCode) {
		this(errorCode, null);
	}

	public AuctionRoomCreationException(
		AuctionRoomCreationErrorCode errorCode,
		Throwable cause
	) {
		super(errorCode.name(), cause);
		this.errorCode = errorCode;
	}

	public AuctionRoomCreationErrorCode getErrorCode() {
		return errorCode;
	}
}
