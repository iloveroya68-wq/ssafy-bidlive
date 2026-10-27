package com.ssafy.bidlive.wallet.application;

/** 거래·베팅 지갑의 총액·홀드액·가용액 조회 결과다. */
public record WalletQueryResult(
	WalletSummary tradeWallet,
	WalletSummary bettingWallet
) {

	public record WalletSummary(
		long balance,
		long heldAmount,
		long availableAmount
	) {
	}
}
