package com.ssafy.bidlive.media.domain;

import java.time.OffsetDateTime;

/** 발급이 끝난 라이브 접속 정보를 방, 세션, 접속 토큰, 권한과 발급 시각으로 표현합니다. */
public record LiveSessionToken(
	String roomId,
	String sessionId,
	String token,
	LiveSessionRole role,
	OffsetDateTime createdAt
) {

	public LiveSessionToken {
		if (roomId == null || roomId.isBlank()) {
			throw new IllegalArgumentException("Live room id must not be blank");
		}
		if (sessionId == null || sessionId.isBlank()) {
			throw new IllegalArgumentException("Live session id must not be blank");
		}
		if (token == null || token.isBlank()) {
			throw new IllegalArgumentException("Live session token must not be blank");
		}
		if (role == null) {
			throw new IllegalArgumentException("Live session role must not be null");
		}
		if (createdAt == null) {
			throw new IllegalArgumentException("Live session createdAt must not be null");
		}
	}
}
