package com.ssafy.bidlive.prediction.domain;

/** PRED-02 결과 공개 전 상태 검증에서 사용하는 오류 코드를 정의한다. */
public enum PredictionResultQueryErrorCode {
	INVALID_AUCTION_ID,
	AUCTION_NOT_FOUND,
	PREDICTION_RESULT_NOT_AVAILABLE
}
