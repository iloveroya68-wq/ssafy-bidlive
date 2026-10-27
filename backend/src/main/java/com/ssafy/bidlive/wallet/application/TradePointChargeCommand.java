package com.ssafy.bidlive.wallet.application;

/** 인증 회원의 거래 포인트 충전을 요청한다. */
public record TradePointChargeCommand(
	Long memberId,
	Long amount,
	String idempotencyKey
) {
}
