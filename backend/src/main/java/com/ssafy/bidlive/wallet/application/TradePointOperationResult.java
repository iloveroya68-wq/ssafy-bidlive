package com.ssafy.bidlive.wallet.application;

import com.ssafy.bidlive.wallet.domain.TradePointOperationType;
import com.ssafy.bidlive.wallet.domain.WalletType;

/** 거래 포인트 작업 후 지갑과 원장 상태다. */
public record TradePointOperationResult(
	WalletType walletType,
	TradePointOperationType operationType,
	long amount,
	long balance,
	long heldAmount,
	long availableAmount,
	Long ledgerId
) {
}
