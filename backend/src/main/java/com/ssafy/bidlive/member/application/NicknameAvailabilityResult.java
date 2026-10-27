package com.ssafy.bidlive.member.application;

import com.ssafy.bidlive.member.domain.NicknameAvailabilityReason;

public record NicknameAvailabilityResult(
	String nickname,
	boolean available,
	NicknameAvailabilityReason reason
) {
}
