package com.ssafy.bidlive.common.security;

import java.util.Objects;

/** 인증 세부정보를 노출하지 않고 STOMP 인증 실패 코드를 전달합니다. */
public class StompAuthenticationException extends RuntimeException {

	private final StompAuthenticationErrorCode errorCode;

	public StompAuthenticationException(StompAuthenticationErrorCode errorCode) {
		super("STOMP authentication failed: " + Objects.requireNonNull(errorCode, "errorCode"));
		this.errorCode = errorCode;
	}

	public StompAuthenticationErrorCode errorCode() {
		return errorCode;
	}
}
