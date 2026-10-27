package com.ssafy.bidlive.auth.domain;

/** OAuth 회원 자동 가입 내부 처리에서 사용하는 서비스 오류 코드다. */
public enum OAuthMemberProvisioningErrorCode {
	SOCIAL_ACCOUNT_ALREADY_EXISTS,
	OAUTH_PROFILE_INSUFFICIENT,
	MEMBER_CREATION_FAILED
}
