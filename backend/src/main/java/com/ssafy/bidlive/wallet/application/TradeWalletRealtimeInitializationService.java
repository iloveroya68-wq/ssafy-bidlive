package com.ssafy.bidlive.wallet.application;

import com.ssafy.bidlive.wallet.application.TradePointOperationPersistence.LockedTradeWallet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL TRADE 지갑 스냅샷을 Redis에 최초 적재한다. */
@Service
public class TradeWalletRealtimeInitializationService {

	private final TradePointOperationPersistence persistence;
	private final TradeWalletRealtimeState realtimeState;

	public TradeWalletRealtimeInitializationService(
		TradePointOperationPersistence persistence,
		TradeWalletRealtimeState realtimeState
	) {
		this.persistence = persistence;
		this.realtimeState = realtimeState;
	}

	@Transactional
	public void initializeIfAbsent(Long memberId) {
		if (memberId == null || memberId <= 0) {
			throw new IllegalArgumentException("memberId must be positive");
		}
		if (realtimeState.isInitialized(memberId)) {
			return;
		}
		LockedTradeWallet wallet = persistence
			.lockTradeWallet(memberId)
			.orElseThrow(() -> new IllegalStateException(
				"TRADE wallet not found for member"
			));
		realtimeState.initializeIfAbsent(
			memberId,
			wallet.availableBalance(),
			wallet.heldBalance()
		);
	}
}
