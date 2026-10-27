package com.ssafy.bidlive.wallet.domain;

/** P0 거래 포인트 환불 금액의 기본 검증 규칙이다. */
public final class TradePointRefundPolicy {

	private TradePointRefundPolicy() {
	}

	public static boolean isPositive(Long amount) {
		return amount != null && amount > 0;
	}
}
