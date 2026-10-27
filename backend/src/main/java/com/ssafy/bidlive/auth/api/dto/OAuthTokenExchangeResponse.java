package com.ssafy.bidlive.auth.api.dto;

import com.ssafy.bidlive.auth.application.OAuthTokenExchangeService;
import com.ssafy.bidlive.member.domain.MemberRole;
import com.ssafy.bidlive.member.domain.MemberStatus;

public record OAuthTokenExchangeResponse(
	String tokenType,
	String accessToken,
	long expiresIn,
	MemberSummary member
) {

	private static final String BEARER_TOKEN_TYPE = "Bearer";

	public static OAuthTokenExchangeResponse from(
		OAuthTokenExchangeService.OAuthTokenExchangeResult result
	) {
		OAuthTokenExchangeService.MemberSummary member = result.member();
		return new OAuthTokenExchangeResponse(
			BEARER_TOKEN_TYPE,
			result.accessToken(),
			result.accessTokenTtl().toSeconds(),
			new MemberSummary(
				member.memberId(),
				member.nickname(),
				member.onboardingRequired(),
				member.status(),
				member.role()
			)
		);
	}

	public record MemberSummary(
		Long memberId,
		String nickname,
		boolean onboardingRequired,
		MemberStatus status,
		MemberRole role
	) {
	}
}
