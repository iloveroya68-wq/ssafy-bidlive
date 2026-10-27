package com.ssafy.bidlive.wallet.application;

/** DB 커밋 뒤 Redis에 반영할 TRADE 지갑 잔액 변경분과 결과 스냅샷이다. */
public record TradeWalletBalanceChangedEvent(
	Long memberId,
	Long ledgerId,
	long availableDelta,
	long heldDelta,
	long availableBalanceAfter,
	long heldBalanceAfter
) {
}
