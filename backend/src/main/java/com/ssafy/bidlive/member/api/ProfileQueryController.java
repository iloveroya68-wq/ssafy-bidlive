package com.ssafy.bidlive.member.api;

import com.ssafy.bidlive.member.api.dto.ProfileQueryResponse;
import com.ssafy.bidlive.member.application.ProfileQueryException;
import com.ssafy.bidlive.member.application.ProfileQueryService;
import com.ssafy.bidlive.member.domain.ProfileQueryErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 인증 회원의 최신 상태와 마이페이지 활동 요약을 조회한다. */
@RestController
@RequestMapping("/api/v1/members/me")
public class ProfileQueryController {

	private final ProfileQueryService profileQueryService;

	public ProfileQueryController(ProfileQueryService profileQueryService) {
		this.profileQueryService = profileQueryService;
	}

	@GetMapping
	public ProfileQueryResponse query(Authentication authentication) {
		return ProfileQueryResponse.from(
			profileQueryService.query(authenticatedMemberId(authentication))
		);
	}

	private Long authenticatedMemberId(Authentication authentication) {
		if (authentication == null || !authentication.isAuthenticated()) {
			throw new ProfileQueryException(ProfileQueryErrorCode.UNAUTHORIZED);
		}
		try {
			Long memberId = Long.valueOf(authentication.getName());
			if (memberId <= 0) {
				throw new NumberFormatException("member ID must be positive");
			}
			return memberId;
		} catch (NumberFormatException exception) {
			throw new ProfileQueryException(
				ProfileQueryErrorCode.UNAUTHORIZED,
				exception
			);
		}
	}
}
