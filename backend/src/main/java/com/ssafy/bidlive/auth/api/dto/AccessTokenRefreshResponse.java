package com.ssafy.bidlive.auth.api.dto;

import com.ssafy.bidlive.auth.application.AccessTokenRefreshService;

public record AccessTokenRefreshResponse(
	String tokenType,
	String accessToken,
	long expiresIn
) {

	private static final String BEARER_TOKEN_TYPE = "Bearer";

	public static AccessTokenRefreshResponse from(
		AccessTokenRefreshService.AccessTokenRefreshResult result
	) {
		return new AccessTokenRefreshResponse(
			BEARER_TOKEN_TYPE,
			result.accessToken(),
			result.accessTokenTtl().toSeconds()
		);
	}
}
