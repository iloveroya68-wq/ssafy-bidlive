package com.ssafy.bidlive.wallet.application;

/** 내부 베팅 포인트 차감이 실패한 원인을 구분한다. */
public enum BettingPointBetErrorCode {
	BETTING_WALLET_NOT_FOUND,
	INSUFFICIENT_BETTING_POINTS,
	IDEMPOTENCY_KEY_REUSED
}
