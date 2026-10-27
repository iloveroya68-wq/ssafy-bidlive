package com.ssafy.bidlive.member.api;

import com.ssafy.bidlive.member.api.dto.SettlementAccountQueryResponse;
import com.ssafy.bidlive.member.application.SettlementAccountQueryException;
import com.ssafy.bidlive.member.application.SettlementAccountQueryService;
import com.ssafy.bidlive.member.domain.SettlementAccountQueryErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 인증 회원 본인의 마스킹된 정산 계좌를 조회한다. */
@RestController
@RequestMapping("/api/v1/members/me/settlement-account")
public class SettlementAccountQueryController {

	private final SettlementAccountQueryService queryService;

	public SettlementAccountQueryController(
		SettlementAccountQueryService queryService
	) {
		this.queryService = queryService;
	}

	@GetMapping
	public SettlementAccountQueryResponse query(
		Authentication authentication
	) {
		return SettlementAccountQueryResponse.from(
			queryService.query(authenticatedMemberId(authentication))
		);
	}

	private Long authenticatedMemberId(Authentication authentication) {
		if (authentication == null || !authentication.isAuthenticated()) {
			throw unauthorized();
		}
		try {
			Long memberId = Long.valueOf(authentication.getName());
			if (memberId <= 0) {
				throw new NumberFormatException("member ID must be positive");
			}
			return memberId;
		} catch (NumberFormatException exception) {
			throw new SettlementAccountQueryException(
				SettlementAccountQueryErrorCode.UNAUTHORIZED,
				exception
			);
		}
	}

	private SettlementAccountQueryException unauthorized() {
		return new SettlementAccountQueryException(
			SettlementAccountQueryErrorCode.UNAUTHORIZED
		);
	}
}
