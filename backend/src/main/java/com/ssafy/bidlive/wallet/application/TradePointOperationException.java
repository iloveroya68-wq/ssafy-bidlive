package com.ssafy.bidlive.wallet.application;

/** 거래 포인트 홀드·해제·정산 규칙 위반을 호출 모듈에 전달한다. */
public class TradePointOperationException extends RuntimeException {

	private final TradePointOperationErrorCode errorCode;

	public TradePointOperationException(
		TradePointOperationErrorCode errorCode
	) {
		super(errorCode.name());
		this.errorCode = errorCode;
	}

	public TradePointOperationException(
		TradePointOperationErrorCode errorCode,
		Throwable cause
	) {
		super(errorCode.name(), cause);
		this.errorCode = errorCode;
	}

	public TradePointOperationErrorCode getErrorCode() {
		return errorCode;
	}
}
