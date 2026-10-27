package com.ssafy.bidlive.auction.application;

import com.ssafy.bidlive.auction.domain.AuctionResultErrorCode;

/** 경매 결과 조회 실패. 오류 코드는 명세 「오류 응답」표를 따른다. */
public class AuctionResultQueryException extends RuntimeException {

	private final transient AuctionResultErrorCode errorCode;

	public AuctionResultQueryException(AuctionResultErrorCode errorCode) {
		super(errorCode.name());
		this.errorCode = errorCode;
	}

	public AuctionResultQueryException(AuctionResultErrorCode errorCode, Throwable cause) {
		super(errorCode.name(), cause);
		this.errorCode = errorCode;
	}

	public AuctionResultErrorCode getErrorCode() {
		return errorCode;
	}
}
