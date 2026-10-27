package com.ssafy.bidlive.auth.domain;

import org.springframework.http.HttpStatus;

public enum LogoutErrorCode {
	INVALID_LOGOUT_REQUEST(HttpStatus.BAD_REQUEST, "로그아웃 요청이 올바르지 않습니다."),
	LOGOUT_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "로그아웃 처리에 실패했습니다.");

	private final HttpStatus httpStatus;
	private final String message;

	LogoutErrorCode(HttpStatus httpStatus, String message) {
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
