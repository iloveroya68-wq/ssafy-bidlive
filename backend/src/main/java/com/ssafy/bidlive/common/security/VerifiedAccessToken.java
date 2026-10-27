package com.ssafy.bidlive.common.security;

import java.time.Instant;
import java.util.Objects;

import com.ssafy.bidlive.member.domain.MemberRole;

/** 검증을 통과한 Access Token의 최소 인증 정보를 표현합니다. */
public record VerifiedAccessToken(
	Long memberId,
	MemberRole role,
	Instant expiresAt
) {

	public VerifiedAccessToken {
		Objects.requireNonNull(memberId, "memberId");
		Objects.requireNonNull(role, "role");
		Objects.requireNonNull(expiresAt, "expiresAt");

		if (memberId <= 0) {
			throw new IllegalArgumentException("memberId는 양수여야 합니다.");
		}
	}
}
