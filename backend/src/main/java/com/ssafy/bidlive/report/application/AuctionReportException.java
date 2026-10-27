package com.ssafy.bidlive.report.application;

import com.ssafy.bidlive.report.domain.AuctionReportErrorCode;

/** 신고를 접수할 수 없을 때 API 계층으로 오류 코드를 전달한다. */
public class AuctionReportException extends RuntimeException {

	private final AuctionReportErrorCode errorCode;

	public AuctionReportException(AuctionReportErrorCode errorCode) {
		super(errorCode.name());
		this.errorCode = errorCode;
	}

	public AuctionReportErrorCode getErrorCode() {
		return errorCode;
	}
}
