package com.ssafy.bidlive.wallet.api.dto;

import com.ssafy.bidlive.wallet.application.TradePointRefundCommand;

/** P0 거래 포인트 환불 금액을 전달한다. */
public record TradePointRefundRequest(Long amount) {

	public TradePointRefundCommand toCommand(
		Long memberId,
		String idempotencyKey
	) {
		return new TradePointRefundCommand(
			memberId,
			amount,
			idempotencyKey
		);
	}
}
