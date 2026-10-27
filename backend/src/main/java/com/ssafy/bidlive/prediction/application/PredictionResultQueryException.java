package com.ssafy.bidlive.prediction.application;

import com.ssafy.bidlive.prediction.domain.PredictionResultQueryErrorCode;

/** PRED-02 상태 검증 실패와 외부에 노출할 오류 코드를 함께 전달한다. */
public class PredictionResultQueryException extends RuntimeException {

	private final PredictionResultQueryErrorCode errorCode;

	public PredictionResultQueryException(
		PredictionResultQueryErrorCode errorCode
	) {
		this.errorCode = errorCode;
	}

	public PredictionResultQueryErrorCode getErrorCode() {
		return errorCode;
	}
}
