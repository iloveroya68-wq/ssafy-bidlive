package com.ssafy.bidlive.wallet.application;

public class TradePointRefundException extends RuntimeException {

	private final TradePointRefundErrorCode errorCode;

	public TradePointRefundException(
		TradePointRefundErrorCode errorCode
	) {
		super(errorCode.name());
		this.errorCode = errorCode;
	}

	public TradePointRefundErrorCode getErrorCode() {
		return errorCode;
	}
}
