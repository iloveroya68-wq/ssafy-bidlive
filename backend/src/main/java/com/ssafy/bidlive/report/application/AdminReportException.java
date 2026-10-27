package com.ssafy.bidlive.report.application;

import com.ssafy.bidlive.report.domain.AdminReportErrorCode;

/** 운영자 신고 목록 조회를 처리할 수 없을 때 API 계층으로 오류 코드를 전달한다. */
public class AdminReportException extends RuntimeException {

	private final AdminReportErrorCode errorCode;

	public AdminReportException(AdminReportErrorCode errorCode) {
		this(errorCode, null);
	}

	public AdminReportException(AdminReportErrorCode errorCode, Throwable cause) {
		super(errorCode.name(), cause);
		this.errorCode = errorCode;
	}

	public AdminReportErrorCode getErrorCode() {
		return errorCode;
	}
}
