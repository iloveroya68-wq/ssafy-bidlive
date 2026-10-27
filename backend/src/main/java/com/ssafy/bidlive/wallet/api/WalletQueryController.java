package com.ssafy.bidlive.wallet.api;

import com.ssafy.bidlive.wallet.api.dto.WalletQueryResponse;
import com.ssafy.bidlive.wallet.application.WalletQueryErrorCode;
import com.ssafy.bidlive.wallet.application.WalletQueryException;
import com.ssafy.bidlive.wallet.application.WalletQueryService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 인증 회원 본인의 거래·베팅 지갑 상태를 조회한다. */
@RestController
@RequestMapping("/api/v1/members/me/wallets")
public class WalletQueryController {

	private final WalletQueryService queryService;

	public WalletQueryController(WalletQueryService queryService) {
		this.queryService = queryService;
	}

	@GetMapping
	public WalletQueryResponse query(Authentication authentication) {
		return WalletQueryResponse.from(
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
				throw new NumberFormatException(
					"member ID must be positive"
				);
			}
			return memberId;
		} catch (NumberFormatException exception) {
			throw new WalletQueryException(
				WalletQueryErrorCode.UNAUTHORIZED,
				exception
			);
		}
	}

	private WalletQueryException unauthorized() {
		return new WalletQueryException(
			WalletQueryErrorCode.UNAUTHORIZED
		);
	}
}
