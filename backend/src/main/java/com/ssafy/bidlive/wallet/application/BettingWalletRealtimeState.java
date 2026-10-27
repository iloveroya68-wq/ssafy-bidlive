package com.ssafy.bidlive.wallet.application;

/** 회원 전역 BETTING 가용 잔액의 최초 적재와 정산 증가를 제공하는 Port다. */
public interface BettingWalletRealtimeState {

	boolean isInitialized(Long memberId);

	void initializeIfAbsent(Long memberId, long availableBalance);

	void increaseAvailableBalance(Long memberId, long amount);
}
