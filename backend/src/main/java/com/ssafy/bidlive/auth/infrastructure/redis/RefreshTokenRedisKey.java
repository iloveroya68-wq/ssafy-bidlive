package com.ssafy.bidlive.auth.infrastructure.redis;

import com.ssafy.bidlive.auth.application.VerifiedRefreshToken;

/** 회원 단위 Hash Tag를 사용해 Refresh Token 세션 Redis 키를 생성한다. */
final class RefreshTokenRedisKey {

	private static final String KEY_FORMAT =
		"bidlive:auth:{%d}:refresh-token:%s";

	private RefreshTokenRedisKey() {
	}

	static String from(VerifiedRefreshToken token) {
		return KEY_FORMAT.formatted(token.memberId(), token.tokenId());
	}
}
