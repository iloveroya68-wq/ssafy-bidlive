package com.ssafy.bidlive.wallet.application;

import com.ssafy.bidlive.wallet.application.BettingPointBetPersistence.LockedWallet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL BETTING 지갑 스냅샷을 Redis에 최초 적재한다. */
@Service
public class BettingWalletRealtimeInitializationService {

	private final BettingPointBetPersistence persistence;
	private final BettingWalletRealtimeState realtimeState;

	public BettingWalletRealtimeInitializationService(
		BettingPointBetPersistence persistence,
		BettingWalletRealtimeState realtimeState
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

		LockedWallet wallet = persistence.lockBettingWallet(memberId)
			.orElseThrow(() -> new IllegalStateException(
				"BETTING wallet not found for member"
			));
		realtimeState.initializeIfAbsent(
			memberId,
			wallet.availableBalance()
		);
	}
}
