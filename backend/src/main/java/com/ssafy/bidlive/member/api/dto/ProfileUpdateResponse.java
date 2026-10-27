package com.ssafy.bidlive.member.api.dto;

import java.time.OffsetDateTime;
import java.time.ZoneId;

import com.ssafy.bidlive.member.application.ProfileUpdateResult;

public record ProfileUpdateResponse(
	Long memberId,
	String nickname,
	boolean onboardingRequired,
	OffsetDateTime updatedAt
) {

	private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

	public static ProfileUpdateResponse from(ProfileUpdateResult result) {
		return new ProfileUpdateResponse(
			result.memberId(),
			result.nickname(),
			result.onboardingRequired(),
			result.updatedAt().atZone(KOREA_ZONE).toOffsetDateTime()
		);
	}
}
