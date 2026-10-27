package com.ssafy.bidlive.wallet.application;

public record TradePointRefundCommand(
	Long memberId,
	Long amount,
	String idempotencyKey
) {
}
