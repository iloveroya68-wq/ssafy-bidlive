package com.ssafy.bidlive.wallet.application;

/** 예측 스테이크를 베팅 지갑에서 차감할 수 없을 때 발생한다. */
public class BettingPointBetException extends RuntimeException {

	private final BettingPointBetErrorCode errorCode;

	public BettingPointBetException(BettingPointBetErrorCode errorCode) {
		super(errorCode.name());
		this.errorCode = errorCode;
	}

	public BettingPointBetErrorCode getErrorCode() {
		return errorCode;
	}
}
