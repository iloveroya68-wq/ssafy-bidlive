package com.ssafy.bidlive.wallet.application;

public class PointLedgerQueryException extends RuntimeException {

	private final PointLedgerQueryErrorCode errorCode;

	public PointLedgerQueryException(PointLedgerQueryErrorCode errorCode) {
		super(errorCode.name());
		this.errorCode = errorCode;
	}

	public PointLedgerQueryException(
		PointLedgerQueryErrorCode errorCode,
		Throwable cause
	) {
		super(errorCode.name(), cause);
		this.errorCode = errorCode;
	}

	public PointLedgerQueryErrorCode getErrorCode() {
		return errorCode;
	}
}
