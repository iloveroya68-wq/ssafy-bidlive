package com.ssafy.bidlive.common.support.redis;

/** 실시간 처리들이 공유하는 회원 전역 Redis 지갑 Key를 제공한다. */
public final class RealtimeWalletRedisKey {

	private static final String WALLET_PREFIX = "bid:{bid}:wallet:";

	private RealtimeWalletRedisKey() {
	}

	public static String tradeAvailableBalancesKey() {
		return WALLET_PREFIX + "available-balances";
	}

	public static String tradeHeldBalancesKey() {
		return WALLET_PREFIX + "held-balances";
	}

	public static String tradeSynchronizationWatermarksKey() {
		return WALLET_PREFIX + "synchronization-watermarks";
	}

	public static String tradeSynchronizedLedgerKey(Long ledgerId) {
		return WALLET_PREFIX + "synchronized-ledger:" + ledgerId;
	}

	public static String bettingAvailableBalancesKey() {
		return WALLET_PREFIX + "betting-available-balances";
	}
}
