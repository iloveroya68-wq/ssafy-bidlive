package com.ssafy.bidlive.wallet.api;

import com.ssafy.bidlive.wallet.api.dto.TradePointRefundRequest;
import com.ssafy.bidlive.wallet.api.dto.TradePointRefundResponse;
import com.ssafy.bidlive.wallet.application.TradePointRefundCommand;
import com.ssafy.bidlive.wallet.application.TradePointRefundErrorCode;
import com.ssafy.bidlive.wallet.application.TradePointRefundException;
import com.ssafy.bidlive.wallet.application.TradePointRefundService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 인증 회원의 P0 거래 포인트 환불 요청을 처리한다. */
@RestController
@RequestMapping("/api/v1/members/me/wallets/trade/refunds")
public class TradePointRefundController {

	private final TradePointRefundService refundService;

	public TradePointRefundController(
		TradePointRefundService refundService
	) {
		this.refundService = refundService;
	}

	@PostMapping
	public TradePointRefundResponse refund(
		@RequestBody(required = false) TradePointRefundRequest request,
		@RequestHeader(
			value = "Idempotency-Key",
			required = false
		) String idempotencyKey,
		Authentication authentication
	) {
		TradePointRefundCommand command = request == null
			? new TradePointRefundCommand(
				authenticatedMemberId(authentication),
				null,
				idempotencyKey
			)
			: request.toCommand(
				authenticatedMemberId(authentication),
				idempotencyKey
			);
		return TradePointRefundResponse.from(
			refundService.refund(command)
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

	private TradePointRefundException unauthorized() {
		return new TradePointRefundException(
			TradePointRefundErrorCode.UNAUTHORIZED
		);
	}
}
