package com.ssafy.bidlive.wallet.application;

/** 베팅 지갑에서 차감된 예측 스테이크 원장 결과를 반환한다. */
public record BettingPointBetResult(
	Long walletId,
	Long ledgerId,
	Long predictionId,
	long amount,
	long availableBalanceAfter,
	boolean duplicated
) {
}
