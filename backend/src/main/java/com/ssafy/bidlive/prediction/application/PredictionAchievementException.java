package com.ssafy.bidlive.prediction.application;

import com.ssafy.bidlive.prediction.domain.PredictionAchievementErrorCode;

/** 예측 성과 조회 실패와 외부에 노출할 오류 코드를 함께 전달한다. */
public class PredictionAchievementException extends RuntimeException {

	private final PredictionAchievementErrorCode errorCode;

	public PredictionAchievementException(PredictionAchievementErrorCode errorCode) {
		this.errorCode = errorCode;
	}

	public PredictionAchievementException(
		PredictionAchievementErrorCode errorCode,
		Throwable cause
	) {
		super(cause);
		this.errorCode = errorCode;
	}

	public PredictionAchievementErrorCode getErrorCode() {
		return errorCode;
	}
}
