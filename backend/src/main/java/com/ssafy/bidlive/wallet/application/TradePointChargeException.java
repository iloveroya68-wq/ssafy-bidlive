package com.ssafy.bidlive.wallet.application;

/** 거래 포인트를 충전할 수 없을 때 계약에 정의된 오류를 전달한다. */
public class TradePointChargeException extends RuntimeException {

	private final TradePointChargeErrorCode errorCode;

	public TradePointChargeException(
		TradePointChargeErrorCode errorCode
	) {
		super(errorCode.name());
		this.errorCode = errorCode;
	}

	public TradePointChargeErrorCode getErrorCode() {
		return errorCode;
	}
}
