package com.ssafy.bidlive.wallet.application;

/** 거래 포인트 홀드·해제·정산 내부 처리 오류 코드다. */
public enum TradePointOperationErrorCode {
	INVALID_HOLD_AMOUNT,
	INSUFFICIENT_AVAILABLE_BALANCE,
	HOLD_NOT_FOUND,
	INVALID_RELEASE_AMOUNT,
	INVALID_SETTLEMENT_AMOUNT,
	DUPLICATE_LEDGER_REFERENCE,
	WALLET_NOT_FOUND
}
