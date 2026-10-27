package com.ssafy.bidlive.member.api.dto;

import com.ssafy.bidlive.member.application.NicknameAvailabilityResult;
import com.ssafy.bidlive.member.domain.NicknameAvailabilityReason;

public record NicknameAvailabilityResponse(
	String nickname,
	boolean available,
	NicknameAvailabilityReason reason
) {

	public static NicknameAvailabilityResponse from(
		NicknameAvailabilityResult result
	) {
		return new NicknameAvailabilityResponse(
			result.nickname(),
			result.available(),
			result.reason()
		);
	}
}
