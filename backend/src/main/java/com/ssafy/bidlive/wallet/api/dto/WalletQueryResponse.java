package com.ssafy.bidlive.wallet.api.dto;

import com.ssafy.bidlive.wallet.application.WalletQueryResult;
import com.ssafy.bidlive.wallet.application.WalletQueryResult.WalletSummary;

/** 거래·베팅 지갑 상태를 함께 전달하는 응답이다. */
public record WalletQueryResponse(
	WalletSummaryResponse tradeWallet,
	WalletSummaryResponse bettingWallet
) {

	public static WalletQueryResponse from(WalletQueryResult result) {
		return new WalletQueryResponse(
			WalletSummaryResponse.from(result.tradeWallet()),
			WalletSummaryResponse.from(result.bettingWallet())
		);
	}

	public record WalletSummaryResponse(
		long balance,
		long heldAmount,
		long availableAmount
	) {

		private static WalletSummaryResponse from(WalletSummary summary) {
			return new WalletSummaryResponse(
				summary.balance(),
				summary.heldAmount(),
				summary.availableAmount()
			);
		}
	}
}
