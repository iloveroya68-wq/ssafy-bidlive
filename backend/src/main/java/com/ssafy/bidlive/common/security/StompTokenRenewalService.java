package com.ssafy.bidlive.common.security;

import com.ssafy.bidlive.member.application.MemberPersistence;
import com.ssafy.bidlive.member.domain.Member;
import com.ssafy.bidlive.member.domain.MemberStatus;
import org.springframework.stereotype.Service;

/** 새 Access Token과 현재 세션 Principal을 검증해 STOMP 재인증 만료 시각을 결정합니다. */
@Service
public class StompTokenRenewalService {

	private final AccessTokenVerifier accessTokenVerifier;
	private final MemberPersistence memberPersistence;

	public StompTokenRenewalService(
		AccessTokenVerifier accessTokenVerifier,
		MemberPersistence memberPersistence
	) {
		this.accessTokenVerifier = accessTokenVerifier;
		this.memberPersistence = memberPersistence;
	}

	public StompTokenRenewalResult renew(
		SecurityPrincipal currentPrincipal,
		String token
	) {
		if (currentPrincipal == null) {
			throw invalidRenewal();
		}

		VerifiedAccessToken verifiedToken = verify(token);
		if (!verifiedToken.memberId().equals(currentPrincipal.memberId())) {
			throw new StompTokenRenewalException(
				StompTokenRenewalErrorCode.AUTH_PRINCIPAL_MISMATCH
			);
		}

		Member member = memberPersistence.findById(verifiedToken.memberId())
			.orElseThrow(this::invalidRenewal);
		if (!isRenewalAllowed(member.getStatus())) {
			throw invalidRenewal();
		}

		return new StompTokenRenewalResult(verifiedToken.expiresAt());
	}

	private VerifiedAccessToken verify(String token) {
		try {
			return accessTokenVerifier.verify(token);
		} catch (AccessTokenVerificationException exception) {
			throw new StompTokenRenewalException(toErrorCode(exception.reason()));
		}
	}

	private boolean isRenewalAllowed(MemberStatus status) {
		return status == MemberStatus.ACTIVE || status == MemberStatus.SUSPENDED;
	}

	private StompTokenRenewalErrorCode toErrorCode(
		AccessTokenFailureReason reason
	) {
		if (reason == AccessTokenFailureReason.EXPIRED) {
			return StompTokenRenewalErrorCode.AUTH_RENEW_EXPIRED;
		}
		return StompTokenRenewalErrorCode.AUTH_RENEW_INVALID;
	}

	private StompTokenRenewalException invalidRenewal() {
		return new StompTokenRenewalException(
			StompTokenRenewalErrorCode.AUTH_RENEW_INVALID
		);
	}
}
