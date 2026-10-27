package com.ssafy.bidlive.member.api;

import com.ssafy.bidlive.member.api.dto.ProfileUpdateRequest;
import com.ssafy.bidlive.member.api.dto.ProfileUpdateResponse;
import com.ssafy.bidlive.member.application.ProfileUpdateCommand;
import com.ssafy.bidlive.member.application.ProfileUpdateException;
import com.ssafy.bidlive.member.application.ProfileUpdateService;
import com.ssafy.bidlive.member.domain.ProfileUpdateErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 인증 회원의 서비스 닉네임을 최초 설정하거나 변경한다. */
@RestController
@RequestMapping("/api/v1/members/me")
public class ProfileUpdateController {

	private final ProfileUpdateService profileUpdateService;

	public ProfileUpdateController(ProfileUpdateService profileUpdateService) {
		this.profileUpdateService = profileUpdateService;
	}

	@PatchMapping
	public ProfileUpdateResponse update(
		@RequestBody(required = false) ProfileUpdateRequest request,
		Authentication authentication
	) {
		ProfileUpdateCommand command = request == null ? null : request.toCommand();
		return ProfileUpdateResponse.from(
			profileUpdateService.update(
				authenticatedMemberId(authentication),
				command
			)
		);
	}

	private Long authenticatedMemberId(Authentication authentication) {
		if (authentication == null || !authentication.isAuthenticated()) {
			throw new ProfileUpdateException(ProfileUpdateErrorCode.UNAUTHORIZED);
		}
		try {
			Long memberId = Long.valueOf(authentication.getName());
			if (memberId <= 0) {
				throw new NumberFormatException("member ID must be positive");
			}
			return memberId;
		} catch (NumberFormatException exception) {
			throw new ProfileUpdateException(
				ProfileUpdateErrorCode.UNAUTHORIZED,
				exception
			);
		}
	}
}
