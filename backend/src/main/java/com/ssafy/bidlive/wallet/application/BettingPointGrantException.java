package com.ssafy.bidlive.wallet.application;

/** 일반 베팅 포인트 지급 실패를 호출 모듈에 전달한다. */
public class BettingPointGrantException extends RuntimeException {

	private final BettingPointGrantErrorCode errorCode;

	public BettingPointGrantException(
		BettingPointGrantErrorCode errorCode
	) {
		super(errorCode.name());
		this.errorCode = errorCode;
	}

	public BettingPointGrantException(
		BettingPointGrantErrorCode errorCode,
		Throwable cause
	) {
		super(errorCode.name(), cause);
		this.errorCode = errorCode;
	}

	public BettingPointGrantErrorCode getErrorCode() {
		return errorCode;
	}
}
