package com.ssafy.bidlive.member.application;

import java.time.Instant;

public record ProfileUpdateResult(
	Long memberId,
	String nickname,
	boolean onboardingRequired,
	Instant updatedAt
) {
}
