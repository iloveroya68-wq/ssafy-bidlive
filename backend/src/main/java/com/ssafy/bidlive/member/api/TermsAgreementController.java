package com.ssafy.bidlive.member.api;

import com.ssafy.bidlive.member.api.dto.TermsAgreementRequest;
import com.ssafy.bidlive.member.api.dto.TermsAgreementResponse;
import com.ssafy.bidlive.member.application.TermsAgreementException;
import com.ssafy.bidlive.member.application.TermsAgreementService;
import com.ssafy.bidlive.member.domain.TermsAgreementErrorCode;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 인증 회원의 온보딩 약관 동의를 검증하고 저장한다. */
@RestController
@RequestMapping("/api/v1/members/me/terms-agreements")
public class TermsAgreementController {

	private final TermsAgreementService termsAgreementService;

	public TermsAgreementController(TermsAgreementService termsAgreementService) {
		this.termsAgreementService = termsAgreementService;
	}

	@PostMapping
	public TermsAgreementResponse agree(
		@Valid @RequestBody TermsAgreementRequest request,
		Authentication authentication
	) {
		return TermsAgreementResponse.from(
			termsAgreementService.agree(
				authenticatedMemberId(authentication),
				request.toCommand()
			)
		);
	}

	private Long authenticatedMemberId(Authentication authentication) {
		if (authentication == null || !authentication.isAuthenticated()) {
			throw new TermsAgreementException(TermsAgreementErrorCode.UNAUTHORIZED);
		}
		try {
			Long memberId = Long.valueOf(authentication.getName());
			if (memberId <= 0) {
				throw new NumberFormatException("member ID must be positive");
			}
			return memberId;
		} catch (NumberFormatException exception) {
			throw new TermsAgreementException(
				TermsAgreementErrorCode.UNAUTHORIZED,
				exception
			);
		}
	}
}
