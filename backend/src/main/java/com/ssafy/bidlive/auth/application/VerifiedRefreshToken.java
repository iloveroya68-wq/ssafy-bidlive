package com.ssafy.bidlive.auth.application;

public record VerifiedRefreshToken(Long memberId, String tokenId) {

	public VerifiedRefreshToken {
		if (memberId == null || memberId <= 0) {
			throw new IllegalArgumentException("memberId must be positive");
		}
		if (tokenId == null || tokenId.isBlank()) {
			throw new IllegalArgumentException("tokenId must not be blank");
		}
	}
}
