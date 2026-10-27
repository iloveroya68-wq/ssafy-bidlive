package com.ssafy.bidlive.common.security;

import com.ssafy.bidlive.member.application.MemberPersistence;
import com.ssafy.bidlive.member.domain.Member;
import com.ssafy.bidlive.member.domain.MemberRole;
import com.ssafy.bidlive.member.domain.MemberStatus;
import org.springframework.stereotype.Service;

/** Access Token과 현재 회원 상태를 검증해 STOMP 세션 인증 정보를 구성합니다. */
@Service
public class StompConnectAuthenticationService {

	private final AccessTokenVerifier accessTokenVerifier;
	private final MemberPersistence memberPersistence;

	public StompConnectAuthenticationService(
		AccessTokenVerifier accessTokenVerifier,
		MemberPersistence memberPersistence
	) {
		this.accessTokenVerifier = accessTokenVerifier;
		this.memberPersistence = memberPersistence;
	}

	public StompAuthenticationResult authenticate(String token) {
		VerifiedAccessToken verifiedToken = verify(token);
		Member member = memberPersistence.findById(verifiedToken.memberId())
			.orElseThrow(this::invalidAuthentication);

		if (!isConnectAllowed(member.getStatus())) {
			throw invalidAuthentication();
		}

		MemberRole currentRole = member.getRole();
		if (currentRole == null) {
			throw invalidAuthentication();
		}

		SecurityPrincipal principal = new SecurityPrincipal(
			verifiedToken.memberId(),
			currentRole
		);
		return new StompAuthenticationResult(principal, verifiedToken.expiresAt());
	}

	private VerifiedAccessToken verify(String token) {
		try {
			return accessTokenVerifier.verify(token);
		} catch (AccessTokenVerificationException exception) {
			throw new StompAuthenticationException(toErrorCode(exception.reason()));
		}
	}

	private boolean isConnectAllowed(MemberStatus status) {
		return status == MemberStatus.ACTIVE || status == MemberStatus.SUSPENDED;
	}

	private StompAuthenticationErrorCode toErrorCode(AccessTokenFailureReason reason) {
		if (reason == AccessTokenFailureReason.EXPIRED) {
			return StompAuthenticationErrorCode.AUTH_EXPIRED;
		}
		return StompAuthenticationErrorCode.AUTH_INVALID;
	}

	private StompAuthenticationException invalidAuthentication() {
		return new StompAuthenticationException(StompAuthenticationErrorCode.AUTH_INVALID);
	}
}
