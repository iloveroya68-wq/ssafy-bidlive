package com.ssafy.bidlive.auction.application;

import com.ssafy.bidlive.auction.domain.CatchUpSummaryErrorCode;

/** AI-03 중간 입장 요약 계약의 검증 실패다. */
public class CatchUpSummaryException extends RuntimeException {

	private final CatchUpSummaryErrorCode code;

	public CatchUpSummaryException(CatchUpSummaryErrorCode code) {
		super(code.name());
		this.code = code;
	}

	public CatchUpSummaryException(CatchUpSummaryErrorCode code, Throwable cause) {
		super(code.name(), cause);
		this.code = code;
	}

	public CatchUpSummaryErrorCode code() {
		return code;
	}
}
