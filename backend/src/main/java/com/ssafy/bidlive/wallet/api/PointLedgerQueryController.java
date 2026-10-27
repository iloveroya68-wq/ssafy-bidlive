package com.ssafy.bidlive.wallet.api;

import com.ssafy.bidlive.wallet.api.dto.PointLedgerQueryResponse;
import com.ssafy.bidlive.wallet.application.PointLedgerQueryErrorCode;
import com.ssafy.bidlive.wallet.application.PointLedgerQueryException;
import com.ssafy.bidlive.wallet.application.PointLedgerQueryService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 인증 회원 본인의 거래·베팅 포인트 원장을 조회한다. */
@RestController
@RequestMapping("/api/v1/members/me/point-ledgers")
public class PointLedgerQueryController {

	private final PointLedgerQueryService queryService;

	public PointLedgerQueryController(
		PointLedgerQueryService queryService
	) {
		this.queryService = queryService;
	}

	@GetMapping
	public PointLedgerQueryResponse query(
		Authentication authentication,
		@RequestParam(required = false) String walletType,
		@RequestParam(required = false) String transactionType,
		@RequestParam(required = false) String cursor,
		@RequestParam(required = false) Integer size
	) {
		return PointLedgerQueryResponse.from(
			queryService.query(
				authenticatedMemberId(authentication),
				walletType,
				transactionType,
				cursor,
				size
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
			throw new PointLedgerQueryException(
				PointLedgerQueryErrorCode.UNAUTHORIZED,
				exception
			);
		}
	}

	private PointLedgerQueryException unauthorized() {
		return new PointLedgerQueryException(
			PointLedgerQueryErrorCode.UNAUTHORIZED
		);
	}
}
