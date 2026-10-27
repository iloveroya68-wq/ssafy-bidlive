package com.ssafy.bidlive.wallet.application;

/** 거래 포인트 충전 실패 원인을 API 계약 단위로 구분한다. */
public enum TradePointChargeErrorCode {
	INVALID_CHARGE_AMOUNT,
	UNAUTHORIZED,
	TRADE_WALLET_NOT_FOUND,
	DUPLICATE_REQUEST,
	CHARGE_LIMIT_EXCEEDED
}
