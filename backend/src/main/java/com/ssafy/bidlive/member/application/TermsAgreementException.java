package com.ssafy.bidlive.member.application;

import com.ssafy.bidlive.member.domain.TermsAgreementErrorCode;

/** 약관 동의 실패와 외부에 노출할 오류 코드를 함께 전달한다. */
public class TermsAgreementException extends RuntimeException {

	private final TermsAgreementErrorCode errorCode;

	public TermsAgreementException(TermsAgreementErrorCode errorCode) {
		this.errorCode = errorCode;
	}

	public TermsAgreementException(TermsAgreementErrorCode errorCode, Throwable cause) {
		super(cause);
		this.errorCode = errorCode;
	}

	public TermsAgreementErrorCode getErrorCode() {
		return errorCode;
	}
}
