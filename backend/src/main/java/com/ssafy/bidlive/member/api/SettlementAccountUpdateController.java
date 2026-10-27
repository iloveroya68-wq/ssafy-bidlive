package com.ssafy.bidlive.member.api;

import com.ssafy.bidlive.member.api.dto.SettlementAccountResponse;
import com.ssafy.bidlive.member.api.dto.SettlementAccountUpdateRequest;
import com.ssafy.bidlive.member.application.SettlementAccountUpdateCommand;
import com.ssafy.bidlive.member.application.SettlementAccountUpdateException;
import com.ssafy.bidlive.member.application.SettlementAccountUpdateService;
import com.ssafy.bidlive.member.domain.SettlementAccountUpdateErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 인증 회원 본인의 정산 계좌를 등록하거나 변경한다. */
@RestController
@RequestMapping("/api/v1/members/me/settlement-account")
public class SettlementAccountUpdateController {

	private final SettlementAccountUpdateService updateService;

	public SettlementAccountUpdateController(
		SettlementAccountUpdateService updateService
	) {
		this.updateService = updateService;
	}

	@PutMapping
	public SettlementAccountResponse update(
		@RequestBody(required = false) SettlementAccountUpdateRequest request,
		Authentication authentication
	) {
		SettlementAccountUpdateCommand command =
			request == null ? null : request.toCommand();
		return SettlementAccountResponse.from(
			updateService.update(
				authenticatedMemberId(authentication),
				command
			)
		);
	}

	private Long authenticatedMemberId(Authentication authentication) {
		if (authentication == null || !authentication.isAuthenticated()) {
			throw unauthorized();
		}
		try {
			Long memberId = Long.valueOf(authentication.getName());
			if (memberId <= 0) {
				throw new NumberFormatException(
					"member ID must be positive"
				);
			}
			return memberId;
		} catch (NumberFormatException exception) {
			throw new SettlementAccountUpdateException(
				SettlementAccountUpdateErrorCode.UNAUTHORIZED,
				exception
			);
		}
	}

	private SettlementAccountUpdateException unauthorized() {
		return new SettlementAccountUpdateException(
			SettlementAccountUpdateErrorCode.UNAUTHORIZED
		);
	}
}
