package com.ssafy.bidlive.wallet.application;

/** 내부 베팅 포인트 정산이 실패한 원인을 구분한다. */
public enum BettingPointSettlementErrorCode {
	BETTING_WALLET_NOT_FOUND,
	IDEMPOTENCY_KEY_REUSED,
	BETTING_POINT_BALANCE_OVERFLOW
}
