package com.ssafy.bidlive.member.domain;

/** 약관 조회 API에서 노출하는 서비스 오류 코드를 정의한다. */
public enum TermsQueryErrorCode {
	INVALID_TERMS_PURPOSE,
	UNAUTHORIZED,
	ACTIVE_TERMS_NOT_FOUND,
	MEMBER_NOT_FOUND,
	TERMS_SERVICE_UNAVAILABLE
}
