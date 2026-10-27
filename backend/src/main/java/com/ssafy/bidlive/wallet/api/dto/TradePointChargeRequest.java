package com.ssafy.bidlive.wallet.api.dto;

import com.ssafy.bidlive.wallet.application.TradePointChargeCommand;

/** P0 거래 포인트 충전 금액을 전달한다. */
public record TradePointChargeRequest(Long amount) {

	public TradePointChargeCommand toCommand(
		Long memberId,
		String idempotencyKey
	) {
		return new TradePointChargeCommand(
			memberId,
			amount,
			idempotencyKey
		);
	}
}
