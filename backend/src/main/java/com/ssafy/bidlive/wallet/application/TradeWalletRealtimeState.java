package com.ssafy.bidlive.wallet.application;

/** 회원 전역 TRADE 지갑의 최초 적재와 커밋 변경 동기화를 제공하는 Port다. */
public interface TradeWalletRealtimeState {

	boolean isInitialized(Long memberId);

	void initializeIfAbsent(
		Long memberId,
		long availableBalance,
		long heldBalance
	);

	void synchronizeCommittedChange(
		TradeWalletBalanceChangedEvent event
	);
}
