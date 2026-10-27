package com.ssafy.bidlive.auction.application;

import com.ssafy.bidlive.auction.domain.AuctionForceEndErrorCode;

/** 강제 종료 실패를 오류 코드와 함께 API 계층으로 올린다. */
public class AuctionForceEndException extends RuntimeException {

	private final AuctionForceEndErrorCode errorCode;

	public AuctionForceEndException(AuctionForceEndErrorCode errorCode) {
		this(errorCode, null);
	}

	public AuctionForceEndException(
		AuctionForceEndErrorCode errorCode,
		Throwable cause
	) {
		super(errorCode.name(), cause);
		this.errorCode = errorCode;
	}

	public AuctionForceEndErrorCode getErrorCode() {
		return errorCode;
	}
}
