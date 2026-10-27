package com.ssafy.bidlive.wallet.application;

/** 지갑 조회 중 클라이언트에 공개할 수 있는 실패 유형이다. */
public enum WalletQueryErrorCode {
	UNAUTHORIZED,
	MEMBER_NOT_FOUND,
	WALLET_NOT_FOUND
}
