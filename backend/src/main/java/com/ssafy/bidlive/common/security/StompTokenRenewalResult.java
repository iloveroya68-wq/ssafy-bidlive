package com.ssafy.bidlive.common.security;

import java.time.Instant;
import java.util.Objects;

/** STOMP 세션 재인증 성공 후 갱신할 Access Token 만료 시각만 전달합니다. */
public record StompTokenRenewalResult(
	Instant tokenExpiresAt
) {

	public StompTokenRenewalResult {
		Objects.requireNonNull(tokenExpiresAt, "tokenExpiresAt");
	}
}
