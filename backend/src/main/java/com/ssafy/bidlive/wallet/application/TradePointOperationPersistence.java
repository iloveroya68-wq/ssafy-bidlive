package com.ssafy.bidlive.wallet.application;

import java.util.Optional;

import com.ssafy.bidlive.wallet.domain.PointLedgerReferenceType;
import com.ssafy.bidlive.wallet.domain.TradePointOperationType;

/** TRADE 지갑 잠금과 홀드 원장 저장에 필요한 영속성 Port다. */
public interface TradePointOperationPersistence {

	void lockIdempotencyKey(String idempotencyKey);

	boolean existsByIdempotencyKey(String idempotencyKey);

	Optional<LockedTradeWallet> lockTradeWallet(Long memberId);

	boolean existsByReferenceAndOperation(
		Long walletId,
		PointLedgerReferenceType referenceType,
		Long referenceId,
		TradePointOperationType operationType
	);

	long findRemainingHold(
		Long walletId,
		PointLedgerReferenceType referenceType,
		Long referenceId
	);

	OperationLedgerRecord save(NewOperation operation);

	record LockedTradeWallet(
		Long walletId,
		Long memberId,
		long availableBalance,
		long heldBalance
	) {
	}

	record NewOperation(
		Long walletId,
		PointLedgerReferenceType referenceType,
		Long referenceId,
		TradePointOperationType operationType,
		long amount,
		long availableBalanceAfter,
		long heldBalanceAfter,
		String idempotencyKey
	) {
		public long availableDelta() {
			return switch (operationType) {
				case HOLD -> -amount;
				case RELEASE -> amount;
				case SETTLEMENT -> 0;
			};
		}

		public long heldDelta() {
			return switch (operationType) {
				case HOLD -> amount;
				case RELEASE, SETTLEMENT -> -amount;
			};
		}
	}

	record OperationLedgerRecord(
		Long ledgerId,
		long availableBalanceAfter,
		long heldBalanceAfter
	) {
	}
}
