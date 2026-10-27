package com.ssafy.bidlive.member.api.dto;

import com.ssafy.bidlive.member.application.ProfileUpdateCommand;

public record ProfileUpdateRequest(String nickname) {

	public ProfileUpdateCommand toCommand() {
		return new ProfileUpdateCommand(nickname);
	}
}
