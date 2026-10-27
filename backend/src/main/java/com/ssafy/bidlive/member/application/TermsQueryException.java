package com.ssafy.bidlive.member.application;

import com.ssafy.bidlive.member.domain.TermsQueryErrorCode;

/** 약관 조회 실패와 외부에 노출할 오류 코드를 함께 전달한다. */
public class TermsQueryException extends RuntimeException {

	private final TermsQueryErrorCode errorCode;

	public TermsQueryException(TermsQueryErrorCode errorCode) {
		this.errorCode = errorCode;
	}

	public TermsQueryException(TermsQueryErrorCode errorCode, Throwable cause) {
		super(cause);
		this.errorCode = errorCode;
	}

	public TermsQueryErrorCode getErrorCode() {
		return errorCode;
	}
}
