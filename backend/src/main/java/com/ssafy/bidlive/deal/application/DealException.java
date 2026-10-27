package com.ssafy.bidlive.deal.application;

import com.ssafy.bidlive.deal.domain.DealErrorCode;

/** 거래 조회·전이를 처리할 수 없을 때 API 계층으로 오류 코드를 전달한다. */
public class DealException extends RuntimeException {

	private final DealErrorCode errorCode;

	public DealException(DealErrorCode errorCode) {
		super(errorCode.name());
		this.errorCode = errorCode;
	}

	public DealErrorCode getErrorCode() {
		return errorCode;
	}
}
