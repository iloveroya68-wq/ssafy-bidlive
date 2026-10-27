package com.ssafy.bidlive.wallet.application;

import com.ssafy.bidlive.wallet.domain.BettingPointSettlementType;

/** 베팅 지갑에 반영된 예측 배당 또는 환불 원장 결과를 반환한다. */
public record BettingPointSettlementResult(
	Long walletId,
	Long ledgerId,
	BettingPointSettlementType settlementType,
	long amount,
	long availableBalanceAfter,
	boolean duplicated
) {
}
