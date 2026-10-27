package com.ssafy.bidlive.auth.domain;

import org.springframework.http.HttpStatus;

public enum OAuthTokenExchangeErrorCode {
	INVALID_LOGIN_CODE(HttpStatus.BAD_REQUEST, "로그인 코드가 올바르지 않습니다."),
	MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "로그인 코드에 연결된 회원을 찾을 수 없습니다."),
	LOGIN_CODE_ALREADY_USED(HttpStatus.CONFLICT, "이미 사용된 로그인 코드입니다."),
	LOGIN_CODE_EXPIRED(HttpStatus.GONE, "만료된 로그인 코드입니다."),
	MEMBER_NOT_AVAILABLE(HttpStatus.FORBIDDEN, "탈퇴한 회원은 로그인할 수 없습니다.");

	private final HttpStatus httpStatus;
	private final String message;

	OAuthTokenExchangeErrorCode(HttpStatus httpStatus, String message) {
		this.httpStatus = httpStatus;
		this.message = message;
	}

	public HttpStatus getHttpStatus() {
		return httpStatus;
	}

	public String getMessage() {
		return message;
	}
}
