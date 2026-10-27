package com.ssafy.bidlive.wallet.domain;

import java.util.Locale;

/** 포인트 원장 조회에서 공개하는 거래 유형이다. */
public enum PointLedgerTransactionType {
	CHARGE,
	HOLD,
	RELEASE,
	SETTLEMENT,
	REFUND,
	GRANT,
	BET,
	PAYOUT;

	public static PointLedgerTransactionType from(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return valueOf(value.trim().toUpperCase(Locale.ROOT));
	}
}
