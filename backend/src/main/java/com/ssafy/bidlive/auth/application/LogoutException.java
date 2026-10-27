package com.ssafy.bidlive.auth.application;

import com.ssafy.bidlive.auth.domain.LogoutErrorCode;

/** 로그아웃 요청 검증 또는 서버 세션 폐기 실패의 서비스 오류 코드를 전달한다. */
public class LogoutException extends RuntimeException {

	private final LogoutErrorCode errorCode;

	public LogoutException(LogoutErrorCode errorCode) {
		super(errorCode.getMessage());
		this.errorCode = errorCode;
	}

	public LogoutException(LogoutErrorCode errorCode, Throwable cause) {
		super(errorCode.getMessage(), cause);
		this.errorCode = errorCode;
	}

	public LogoutErrorCode getErrorCode() {
		return errorCode;
	}
}
