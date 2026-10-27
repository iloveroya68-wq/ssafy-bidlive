package com.ssafy.bidlive.wallet.application;

/** 일반 베팅 포인트 지급 실패 코드다. */
public enum BettingPointGrantErrorCode {
	MEMBER_NOT_FOUND,
	BETTING_WALLET_NOT_FOUND,
	INVALID_GRANT_TYPE,
	INVALID_GRANT_AMOUNT,
	MEMBER_INACTIVE,
	POINT_LEDGER_WRITE_FAILED
}
