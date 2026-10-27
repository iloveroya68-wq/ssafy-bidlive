package com.ssafy.bidlive.wallet.application;

import java.time.Instant;

public record TradePointRefundResult(
	long refundedAmount,
	long balance,
	long heldAmount,
	long availableAmount,
	Long ledgerId,
	Instant refundedAt
) {
}
