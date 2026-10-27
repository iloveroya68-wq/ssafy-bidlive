package com.ssafy.bidlive.member.api;

import com.ssafy.bidlive.member.api.dto.TermsResponse;
import com.ssafy.bidlive.member.application.TermsQueryException;
import com.ssafy.bidlive.member.application.TermsQueryService;
import com.ssafy.bidlive.member.domain.TermsQueryErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 인증 회원에게 현재 적용되는 최신 약관과 동의 여부를 제공한다. */
@RestController
@RequestMapping("/api/v1/terms")
public class TermsController {

	private final TermsQueryService termsQueryService;

	public TermsController(TermsQueryService termsQueryService) {
		this.termsQueryService = termsQueryService;
	}

	@GetMapping
	public TermsResponse getTerms(
		@RequestParam(required = false) String purpose,
		Authentication authentication
	) {
		return TermsResponse.from(
			termsQueryService.getTerms(authenticatedMemberId(authentication), purpose)
		);
	}

	private Long authenticatedMemberId(Authentication authentication) {
		if (authentication == null || !authentication.isAuthenticated()) {
			throw new TermsQueryException(TermsQueryErrorCode.UNAUTHORIZED);
		}
		try {
			Long memberId = Long.valueOf(authentication.getName());
			if (memberId <= 0) {
				throw new NumberFormatException("member ID must be positive");
			}
			return memberId;
		} catch (NumberFormatException exception) {
			throw new TermsQueryException(TermsQueryErrorCode.UNAUTHORIZED, exception);
		}
	}
}
