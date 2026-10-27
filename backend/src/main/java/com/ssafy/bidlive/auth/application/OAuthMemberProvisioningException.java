package com.ssafy.bidlive.auth.application;

import com.ssafy.bidlive.auth.domain.OAuthMemberProvisioningErrorCode;

/** OAuth 회원 자동 가입 실패를 내부 서비스 오류 코드와 함께 전달한다. */
public class OAuthMemberProvisioningException extends RuntimeException {

	private final OAuthMemberProvisioningErrorCode errorCode;

	private OAuthMemberProvisioningException(
		OAuthMemberProvisioningErrorCode errorCode,
		String message,
		Throwable cause
	) {
		super(message, cause);
		this.errorCode = errorCode;
	}

	public static OAuthMemberProvisioningException socialAccountAlreadyExists(Throwable cause) {
		return new OAuthMemberProvisioningException(
			OAuthMemberProvisioningErrorCode.SOCIAL_ACCOUNT_ALREADY_EXISTS,
			"동일한 카카오 계정 연결 정보가 이미 존재합니다.",
			cause
		);
	}

	public static OAuthMemberProvisioningException profileInsufficient() {
		return new OAuthMemberProvisioningException(
			OAuthMemberProvisioningErrorCode.OAUTH_PROFILE_INSUFFICIENT,
			"OAuth 회원 식별에 필요한 프로필 정보가 부족합니다.",
			null
		);
	}

	public static OAuthMemberProvisioningException memberCreationFailed(Throwable cause) {
		return new OAuthMemberProvisioningException(
			OAuthMemberProvisioningErrorCode.MEMBER_CREATION_FAILED,
			"OAuth 회원과 지갑을 생성하지 못했습니다.",
			cause
		);
	}

	public OAuthMemberProvisioningErrorCode getErrorCode() {
		return errorCode;
	}
}
