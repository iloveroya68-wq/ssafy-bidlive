package com.ssafy.bidlive.member.api;

import com.ssafy.bidlive.member.api.dto.NicknameAvailabilityResponse;
import com.ssafy.bidlive.member.application.NicknameAvailabilityException;
import com.ssafy.bidlive.member.application.NicknameAvailabilityService;
import com.ssafy.bidlive.member.domain.NicknameAvailabilityErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 프로필 저장 전에 서비스 닉네임의 사용 가능 여부를 조회한다. */
@RestController
@RequestMapping("/api/v1/members/nickname-availability")
public class NicknameAvailabilityController {

	private final NicknameAvailabilityService nicknameAvailabilityService;

	public NicknameAvailabilityController(
		NicknameAvailabilityService nicknameAvailabilityService
	) {
		this.nicknameAvailabilityService = nicknameAvailabilityService;
	}

	@GetMapping
	public NicknameAvailabilityResponse check(
		@RequestParam(required = false) String nickname,
		Authentication authentication
	) {
		return NicknameAvailabilityResponse.from(
			nicknameAvailabilityService.check(
				authenticatedMemberId(authentication),
				nickname
			)
		);
	}

	private Long authenticatedMemberId(Authentication authentication) {
		if (authentication == null || !authentication.isAuthenticated()) {
			throw new NicknameAvailabilityException(
				NicknameAvailabilityErrorCode.UNAUTHORIZED
			);
		}
		try {
			Long memberId = Long.valueOf(authentication.getName());
			if (memberId <= 0) {
				throw new NumberFormatException("member ID must be positive");
			}
			return memberId;
		} catch (NumberFormatException exception) {
			throw new NicknameAvailabilityException(
				NicknameAvailabilityErrorCode.UNAUTHORIZED,
				exception
			);
		}
	}
}
