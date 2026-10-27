package com.ssafy.bidlive.member.application;

import com.ssafy.bidlive.member.domain.HostedAuctionQueryErrorCode;

/** 개최 경매 조회 실패와 명세 오류 코드를 함께 전달한다. */
public class HostedAuctionQueryException extends RuntimeException {

	private final HostedAuctionQueryErrorCode errorCode;

	public HostedAuctionQueryException(HostedAuctionQueryErrorCode errorCode) {
		this.errorCode = errorCode;
	}

	public HostedAuctionQueryException(
		HostedAuctionQueryErrorCode errorCode,
		Throwable cause
	) {
		super(cause);
		this.errorCode = errorCode;
	}

	public HostedAuctionQueryErrorCode getErrorCode() {
		return errorCode;
	}
}
