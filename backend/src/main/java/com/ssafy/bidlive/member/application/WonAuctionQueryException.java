package com.ssafy.bidlive.member.application;

import com.ssafy.bidlive.member.domain.WonAuctionQueryErrorCode;

/** 낙찰 받은 경매 조회 실패와 명세 오류 코드를 함께 전달한다. */
public class WonAuctionQueryException extends RuntimeException {

	private final WonAuctionQueryErrorCode errorCode;

	public WonAuctionQueryException(WonAuctionQueryErrorCode errorCode) {
		this.errorCode = errorCode;
	}

	public WonAuctionQueryException(
		WonAuctionQueryErrorCode errorCode,
		Throwable cause
	) {
		super(cause);
		this.errorCode = errorCode;
	}

	public WonAuctionQueryErrorCode getErrorCode() {
		return errorCode;
	}
}
