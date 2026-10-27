package com.ssafy.bidlive.common.security;

import java.util.Objects;

/** JWT 원문이나 회원 정보를 포함하지 않고 STOMP 토큰 재인증 실패 코드를 전달합니다. */
public class StompTokenRenewalException extends RuntimeException {

	private final StompTokenRenewalErrorCode errorCode;

	public StompTokenRenewalException(StompTokenRenewalErrorCode errorCode) {
		super(Objects.requireNonNull(errorCode, "errorCode").publicMessage());
		this.errorCode = errorCode;
	}

	public StompTokenRenewalErrorCode errorCode() {
		return errorCode;
	}
}
