package com.ssafy.bidlive.member.api.dto;

import com.ssafy.bidlive.member.application.ProfileQueryResult;
import com.ssafy.bidlive.member.domain.MemberRole;
import com.ssafy.bidlive.member.domain.MemberStatus;

public record ProfileQueryResponse(
	Long memberId,
	String nickname,
	MemberStatus status,
	MemberRole role,
	ProfileActivitySummaryResponse activitySummary
) {

	public static ProfileQueryResponse from(ProfileQueryResult result) {
		return new ProfileQueryResponse(
			result.memberId(),
			result.nickname(),
			result.status(),
			result.role(),
			ProfileActivitySummaryResponse.from(result.activitySummary())
		);
	}
}
