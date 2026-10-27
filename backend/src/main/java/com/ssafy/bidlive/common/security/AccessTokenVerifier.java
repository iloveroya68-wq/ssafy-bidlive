package com.ssafy.bidlive.common.security;

/** Access Token을 검증하고 신뢰할 수 있는 인증 정보를 반환하는 계약입니다. */
public interface AccessTokenVerifier {

	VerifiedAccessToken verify(String token);
}
