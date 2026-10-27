package com.ssafy.bidlive.auth.application;

import com.ssafy.bidlive.auth.domain.AccessTokenRefreshErrorCode;

/** Access Token 재발급 실패에 대응하는 서비스 오류 코드를 전달한다. */
public class AccessTokenRefreshException extends RuntimeException {

	private final AccessTokenRefreshErrorCode errorCode;

	public AccessTokenRefreshException(AccessTokenRefreshErrorCode errorCode) {
		super(errorCode.getMessage());
		this.errorCode = errorCode;
	}

	public AccessTokenRefreshErrorCode getErrorCode() {
		return errorCode;
	}
}
