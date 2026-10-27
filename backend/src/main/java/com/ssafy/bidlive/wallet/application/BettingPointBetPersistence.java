package com.ssafy.bidlive.wallet.application;

import java.util.Optional;

/** 베팅 지갑 잠금과 BET 원장 기록에 필요한 영속성 Port다. */
public interface BettingPointBetPersistence {

	Optional<LedgerRecord> findByIdempotencyKey(String idempotencyKey);

	Optional<LockedWallet> lockBettingWallet(Long memberId);

	LedgerRecord save(NewBet bet);

	record LockedWallet(
		Long walletId,
		Long memberId,
		long availableBalance,
		long heldBalance
	) {
	}

	record NewBet(
		Long walletId,
		Long memberId,
		Long predictionId,
		long amount,
		long availableBalanceAfter,
		long heldBalanceAfter,
		String idempotencyKey
	) {
	}

	record LedgerRecord(
		Long ledgerId,
		Long walletId,
		Long memberId,
		Long predictionId,
		boolean betTransaction,
		long availableDelta,
		long availableBalanceAfter,
		long heldBalanceAfter,
		String idempotencyKey
	) {
	}
}
