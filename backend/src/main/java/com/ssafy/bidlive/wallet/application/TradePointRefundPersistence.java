package com.ssafy.bidlive.wallet.application;

import java.time.Instant;
import java.util.Optional;

/** 거래 포인트 지갑 잠금과 REFUND 원장 저장에 필요한 영속성 Port다. */
public interface TradePointRefundPersistence {

	void lockIdempotencyKey(String idempotencyKey);

	boolean existsByIdempotencyKey(String idempotencyKey);

	Optional<LockedTradeWallet> lockTradeWallet(Long memberId);

	RefundLedgerRecord save(NewRefund refund);

	record LockedTradeWallet(
		Long walletId,
		Long memberId,
		long availableBalance,
		long heldBalance
	) {
	}

	record NewRefund(
		Long walletId,
		long amount,
		long availableBalanceAfter,
		long heldBalanceAfter,
		String idempotencyKey
	) {
	}

	record RefundLedgerRecord(
		Long ledgerId,
		long availableBalanceAfter,
		long heldBalanceAfter,
		Instant refundedAt
	) {
	}
}
