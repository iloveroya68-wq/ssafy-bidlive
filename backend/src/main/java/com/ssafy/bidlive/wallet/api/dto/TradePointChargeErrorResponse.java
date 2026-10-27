package com.ssafy.bidlive.wallet.api.dto;

/** 거래 포인트 충전 실패 코드와 사용자 메시지를 반환한다. */
public record TradePointChargeErrorResponse(
	String code,
	String message
) {
}
