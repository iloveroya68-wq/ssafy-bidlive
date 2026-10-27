package com.ssafy.bidlive.wallet.application;

import java.time.Instant;

/** 충전 후 거래 포인트 지갑과 생성된 원장 정보를 반환한다. */
public record TradePointChargeResult(
	long chargedAmount,
	long balance,
	long heldAmount,
	long availableAmount,
	Long ledgerId,
	Instant chargedAt
) {
}
