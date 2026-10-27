package com.ssafy.bidlive.wallet.application;

import java.time.Instant;
import java.util.List;

import com.ssafy.bidlive.wallet.domain.PointLedgerReferenceType;
import com.ssafy.bidlive.wallet.domain.PointLedgerTransactionType;
import com.ssafy.bidlive.wallet.domain.WalletType;

public record PointLedgerQueryResult(
	List<Item> items,
	String nextCursor,
	boolean hasNext
) {

	public record Item(
		long ledgerId,
		WalletType walletType,
		PointLedgerTransactionType transactionType,
		long availableChange,
		long heldChange,
		long availableBalanceAfter,
		long heldBalanceAfter,
		String description,
		PointLedgerReferenceType referenceType,
		Long referenceId,
		Instant createdAt
	) {
	}
}
