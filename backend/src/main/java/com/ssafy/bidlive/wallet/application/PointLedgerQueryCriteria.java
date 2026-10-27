package com.ssafy.bidlive.wallet.application;

import com.ssafy.bidlive.wallet.domain.PointLedgerCursor;
import com.ssafy.bidlive.wallet.domain.PointLedgerTransactionType;
import com.ssafy.bidlive.wallet.domain.WalletType;

public record PointLedgerQueryCriteria(
	long memberId,
	WalletType walletType,
	PointLedgerTransactionType transactionType,
	PointLedgerCursor cursor,
	int size
) {
}
