package com.ssafy.bidlive.wallet.api.dto;

import java.time.OffsetDateTime;
import java.time.ZoneId;

import com.ssafy.bidlive.wallet.application.TradePointRefundResult;

/** 환불된 거래 포인트와 변경된 지갑 잔액을 반환한다. */
public record TradePointRefundResponse(
	String walletType,
	long refundedAmount,
	long balance,
	long heldAmount,
	long availableAmount,
	Long ledgerId,
	OffsetDateTime refundedAt
) {

	private static final ZoneId RESPONSE_ZONE = ZoneId.of("Asia/Seoul");

	public static TradePointRefundResponse from(
		TradePointRefundResult result
	) {
		return new TradePointRefundResponse(
			"TRADE",
			result.refundedAmount(),
			result.balance(),
			result.heldAmount(),
			result.availableAmount(),
			result.ledgerId(),
			result.refundedAt()
				.atZone(RESPONSE_ZONE)
				.toOffsetDateTime()
		);
	}
}
