package com.ssafy.bidlive.wallet.application;

/** 예측 배당·환불을 베팅 지갑에 반영할 수 없을 때 발생한다. */
public class BettingPointSettlementException extends RuntimeException {

	private final BettingPointSettlementErrorCode errorCode;

	public BettingPointSettlementException(
		BettingPointSettlementErrorCode errorCode
	) {
		super(errorCode.name());
		this.errorCode = errorCode;
	}

	public BettingPointSettlementException(
		BettingPointSettlementErrorCode errorCode,
		Throwable cause
	) {
		super(errorCode.name(), cause);
		this.errorCode = errorCode;
	}

	public BettingPointSettlementErrorCode getErrorCode() {
		return errorCode;
	}
}
