package com.ssafy.bidlive.common.security;

import java.time.Instant;
import java.util.Objects;

/** STOMP CONNECT 이후 세션에 저장할 검증된 인증 정보를 표현합니다. */
public record StompAuthenticationResult(
	SecurityPrincipal principal,
	Instant tokenExpiresAt
) {

	public StompAuthenticationResult {
		Objects.requireNonNull(principal, "principal");
		Objects.requireNonNull(tokenExpiresAt, "tokenExpiresAt");
	}
}
