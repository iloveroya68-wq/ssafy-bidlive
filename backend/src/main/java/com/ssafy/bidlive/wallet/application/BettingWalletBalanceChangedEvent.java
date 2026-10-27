package com.ssafy.bidlive.wallet.application;

/** DB 커밋 뒤 Redis에 반영할 BETTING 지갑 가용 잔액 증가분이다. */
public record BettingWalletBalanceChangedEvent(
	Long memberId,
	long availableDelta
) {
}
