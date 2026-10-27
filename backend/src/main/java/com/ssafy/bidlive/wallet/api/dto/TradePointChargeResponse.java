package com.ssafy.bidlive.wallet.api.dto;

import java.time.OffsetDateTime;

import com.ssafy.bidlive.wallet.application.TradePointChargeResult;
import com.ssafy.bidlive.wallet.domain.TradePointChargePolicy;

/** 충전된 거래 포인트와 변경된 지갑 잔액을 반환한다. */
public record TradePointChargeResponse(
	String walletType,
	long chargedAmount,
	long balance,
	long heldAmount,
	long availableAmount,
	Long ledgerId,
	OffsetDateTime chargedAt
) {

	public static TradePointChargeResponse from(
		TradePointChargeResult result
	) {
		return new TradePointChargeResponse(
			"TRADE",
			result.chargedAmount(),
			result.balance(),
			result.heldAmount(),
			result.availableAmount(),
			result.ledgerId(),
			result.chargedAt()
				.atZone(TradePointChargePolicy.DAILY_LIMIT_ZONE)
				.toOffsetDateTime()
		);
	}
}
