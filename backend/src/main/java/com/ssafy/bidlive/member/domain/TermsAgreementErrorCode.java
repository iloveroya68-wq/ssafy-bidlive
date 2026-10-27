package com.ssafy.bidlive.member.domain;

/** 약관 동의 API에서 노출하는 서비스 오류 코드를 정의한다. */
public enum TermsAgreementErrorCode {
	INVALID_TERMS_REQUEST,
	DUPLICATE_TERM_ID,
	UNAUTHORIZED,
	MEMBER_NOT_FOUND,
	TERM_NOT_FOUND,
	REQUIRED_TERMS_NOT_AGREED,
	TERMS_VERSION_OUTDATED,
	TERM_NOT_ACTIVE,
	TERMS_AGREEMENT_UNAVAILABLE
}
