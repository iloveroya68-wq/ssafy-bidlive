package com.ssafy.bidlive.prediction.application;

import com.ssafy.bidlive.prediction.domain.PredictionParticipationFailureCode;

/** PRED-01 참여 요청이 계약상 비즈니스 조건을 충족하지 못할 때 발생한다. */
public class PredictionRejectedException extends RuntimeException {

	private final PredictionParticipationFailureCode failureCode;

	public PredictionRejectedException(
		PredictionParticipationFailureCode failureCode
	) {
		super(failureCode.name());
		this.failureCode = failureCode;
	}

	public PredictionRejectedException(
		PredictionParticipationFailureCode failureCode,
		Throwable cause
	) {
		super(failureCode.name(), cause);
		this.failureCode = failureCode;
	}

	public PredictionParticipationFailureCode getFailureCode() {
		return failureCode;
	}
}
