package com.ssafy.bidlive.member.application;

import com.ssafy.bidlive.member.domain.MemberRole;
import com.ssafy.bidlive.member.domain.MemberStatus;

public record ProfileQueryResult(
	Long memberId,
	String nickname,
	MemberStatus status,
	MemberRole role,
	ProfileActivitySummary activitySummary
) {
}
