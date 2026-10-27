package com.ssafy.bidlive.common.security;

import java.util.Objects;

/** 토큰 원문을 노출하지 않고 Access Token 검증 실패 사유를 전달합니다. */
public class AccessTokenVerificationException extends RuntimeException {

	private final AccessTokenFailureReason reason;

	public AccessTokenVerificationException(AccessTokenFailureReason reason) {
		this(reason, null);
	}

	public AccessTokenVerificationException(
		AccessTokenFailureReason reason,
		Throwable cause
	) {
		super("Access Token verification failed: " + Objects.requireNonNull(reason, "reason"), cause);
		this.reason = reason;
	}

	public AccessTokenFailureReason reason() {
		return reason;
	}
}
