package com.ssafy.bidlive.wallet.application;

import java.util.Optional;

import com.ssafy.bidlive.wallet.domain.BettingPointSettlementType;

/** 베팅 지갑 잠금과 예측 정산 원장 기록에 필요한 영속성 Port다. */
public interface BettingPointSettlementPersistence {

	Optional<SettlementRecord> findByIdempotencyKey(String idempotencyKey);

	Optional<LockedWallet> lockBettingWallet(Long memberId);

	SettlementRecord save(NewSettlement settlement);

	record LockedWallet(
		Long walletId,
		Long memberId,
		long availableBalance,
		long heldBalance
	) {
	}

	record NewSettlement(
		Long walletId,
		Long memberId,
		Long predictionId,
		Long predictionResultId,
		BettingPointSettlementType settlementType,
		long amount,
		long availableBalanceAfter,
		long heldBalanceAfter,
		String idempotencyKey
	) {
	}

	record SettlementRecord(
		Long ledgerId,
		Long walletId,
		Long memberId,
		Long predictionId,
		Long predictionResultId,
		BettingPointSettlementType settlementType,
		long amount,
		long availableBalanceAfter,
		long heldBalanceAfter,
		String idempotencyKey
	) {
	}
}
