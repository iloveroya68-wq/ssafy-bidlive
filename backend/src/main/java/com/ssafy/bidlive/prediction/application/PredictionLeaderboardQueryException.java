package com.ssafy.bidlive.prediction.application;

import com.ssafy.bidlive.prediction.domain.PredictionLeaderboardQueryErrorCode;

/** PRED-05 조회 실패 사유를 API 계층까지 전달한다. */
public class PredictionLeaderboardQueryException extends RuntimeException {

	private final PredictionLeaderboardQueryErrorCode errorCode;

	public PredictionLeaderboardQueryException(
		PredictionLeaderboardQueryErrorCode errorCode
	) {
		this(errorCode, null);
	}

	public PredictionLeaderboardQueryException(
		PredictionLeaderboardQueryErrorCode errorCode,
		Throwable cause
	) {
		super(errorCode.name(), cause);
		this.errorCode = errorCode;
	}

	public PredictionLeaderboardQueryErrorCode getErrorCode() {
		return errorCode;
	}
}
