package com.ssafy.bidlive.prediction.api;

/** PRED-01 요청에서 유효한 인증 회원을 식별하지 못할 때 발생한다. */
public class PredictionUnauthenticatedException extends RuntimeException {

	public PredictionUnauthenticatedException() {
		super("Authentication is invalid");
	}

	public PredictionUnauthenticatedException(Throwable cause) {
		super("Authentication is invalid", cause);
	}
}
