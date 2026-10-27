package com.ssafy.bidlive.common.security;

/** Access Token 검증 실패의 내부 분류입니다. */
public enum AccessTokenFailureReason {
	MISSING,
	EXPIRED,
	INVALID_SIGNATURE,
	INVALID_ISSUER,
	INVALID_TOKEN_TYPE,
	INVALID_CLAIMS,
	MALFORMED
}
