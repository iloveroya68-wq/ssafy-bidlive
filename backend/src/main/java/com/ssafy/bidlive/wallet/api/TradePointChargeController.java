package com.ssafy.bidlive.wallet.api;

import java.util.UUID;

import com.ssafy.bidlive.wallet.api.dto.TradePointChargeRequest;
import com.ssafy.bidlive.wallet.api.dto.TradePointChargeResponse;
import com.ssafy.bidlive.wallet.application.TradePointChargeCommand;
import com.ssafy.bidlive.wallet.application.TradePointChargeException;
import com.ssafy.bidlive.wallet.application.TradePointChargeService;
import com.ssafy.bidlive.wallet.application.TradePointChargeErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 인증 회원의 P0 거래 포인트 원클릭 충전 요청을 처리한다. */
@RestController
@RequestMapping("/api/v1/members/me/wallets/trade/charge")
public class TradePointChargeController {

	private final TradePointChargeService chargeService;

	public TradePointChargeController(
		TradePointChargeService chargeService
	) {
		this.chargeService = chargeService;
	}

	@PostMapping
	public TradePointChargeResponse charge(
		@RequestBody(required = false) TradePointChargeRequest request,
		@RequestHeader("Idempotency-Key") UUID idempotencyKey,
		Authentication authentication
	) {
		TradePointChargeCommand command = request == null
			? new TradePointChargeCommand(
				authenticatedMemberId(authentication),
				null,
				idempotencyKey.toString()
			)
			: request.toCommand(
				authenticatedMemberId(authentication),
				idempotencyKey.toString()
			);
		return TradePointChargeResponse.from(
			chargeService.charge(command)
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
			throw unauthorized();
		}
	}

	private TradePointChargeException unauthorized() {
		return new TradePointChargeException(
			TradePointChargeErrorCode.UNAUTHORIZED
		);
	}
}
