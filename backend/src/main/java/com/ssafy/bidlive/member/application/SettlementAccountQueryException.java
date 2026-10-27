package com.ssafy.bidlive.member.application;

import com.ssafy.bidlive.member.domain.SettlementAccountQueryErrorCode;

/** 정산 계좌 조회 실패와 외부 오류 코드를 함께 전달한다. */
public class SettlementAccountQueryException extends RuntimeException {

	private final SettlementAccountQueryErrorCode errorCode;

	public SettlementAccountQueryException(
		SettlementAccountQueryErrorCode errorCode
	) {
		this.errorCode = errorCode;
	}

	public SettlementAccountQueryException(
		SettlementAccountQueryErrorCode errorCode,
		Throwable cause
	) {
		super(cause);
		this.errorCode = errorCode;
	}

	public SettlementAccountQueryErrorCode getErrorCode() {
		return errorCode;
	}
}
