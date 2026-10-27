package com.ssafy.bidlive.wallet.application;

import java.time.Instant;

import com.ssafy.bidlive.wallet.domain.PointLedgerReferenceType;
import com.ssafy.bidlive.wallet.domain.PointLedgerTransactionType;
import com.ssafy.bidlive.wallet.domain.WalletType;

public record PointLedgerQueryRow(
	long ledgerId,
	WalletType walletType,
	PointLedgerTransactionType transactionType,
	long availableChange,
	long heldChange,
	long availableBalanceAfter,
	long heldBalanceAfter,
	PointLedgerReferenceType referenceType,
	Long referenceId,
	Instant createdAt
) {
}
