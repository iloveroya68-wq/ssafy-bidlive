package com.ssafy.bidlive.wallet.application;

/** 지갑 조회 실패와 공개 오류 코드를 함께 전달한다. */
public class WalletQueryException extends RuntimeException {

	private final WalletQueryErrorCode errorCode;

	public WalletQueryException(WalletQueryErrorCode errorCode) {
		super(errorCode.name());
		this.errorCode = errorCode;
	}

	public WalletQueryException(
		WalletQueryErrorCode errorCode,
		Throwable cause
	) {
		super(errorCode.name(), cause);
		this.errorCode = errorCode;
	}

	public WalletQueryErrorCode getErrorCode() {
		return errorCode;
	}
}
