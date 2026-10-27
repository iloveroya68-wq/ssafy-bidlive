package com.ssafy.bidlive.member.application;

import com.ssafy.bidlive.member.domain.ParticipatedAuctionQueryErrorCode;

/** 참여 경매 조회 실패와 명세 오류 코드를 함께 전달한다. */
public class ParticipatedAuctionQueryException extends RuntimeException {

	private final ParticipatedAuctionQueryErrorCode errorCode;

	public ParticipatedAuctionQueryException(
		ParticipatedAuctionQueryErrorCode errorCode
	) {
		this.errorCode = errorCode;
	}

	public ParticipatedAuctionQueryException(
		ParticipatedAuctionQueryErrorCode errorCode,
		Throwable cause
	) {
		super(cause);
		this.errorCode = errorCode;
	}

	public ParticipatedAuctionQueryErrorCode getErrorCode() {
		return errorCode;
	}
}
