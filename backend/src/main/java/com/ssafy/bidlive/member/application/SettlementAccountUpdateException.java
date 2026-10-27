package com.ssafy.bidlive.member.application;

import com.ssafy.bidlive.member.domain.SettlementAccountUpdateErrorCode;

/** 정산 계좌 등록·변경 실패와 명세 오류 코드를 함께 전달한다. */
public class SettlementAccountUpdateException extends RuntimeException {

	private final SettlementAccountUpdateErrorCode errorCode;

	public SettlementAccountUpdateException(
		SettlementAccountUpdateErrorCode errorCode
	) {
		super(errorCode.name());
		this.errorCode = errorCode;
	}

	public SettlementAccountUpdateException(
		SettlementAccountUpdateErrorCode errorCode,
		Throwable cause
	) {
		super(errorCode.name(), cause);
		this.errorCode = errorCode;
	}

	public SettlementAccountUpdateErrorCode getErrorCode() {
		return errorCode;
	}
}
