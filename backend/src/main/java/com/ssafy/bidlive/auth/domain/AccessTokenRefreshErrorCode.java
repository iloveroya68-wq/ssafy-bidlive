package com.ssafy.bidlive.auth.domain;

import org.springframework.http.HttpStatus;

public enum AccessTokenRefreshErrorCode {
	REFRESH_TOKEN_MISSING(HttpStatus.UNAUTHORIZED, "Refresh Token 쿠키가 없습니다."),
	REFRESH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "Refresh Token이 올바르지 않습니다."),
	REFRESH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "Refresh Token이 만료되었습니다."),
	MEMBER_UNAVAILABLE(HttpStatus.FORBIDDEN, "현재 사용할 수 없는 회원입니다.");

	private final HttpStatus httpStatus;
	private final String message;

	AccessTokenRefreshErrorCode(HttpStatus httpStatus, String message) {
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
