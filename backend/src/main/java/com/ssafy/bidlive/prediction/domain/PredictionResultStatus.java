package com.ssafy.bidlive.prediction.domain;

/** 예측 정산 결과가 적중·실패·환불 중 어떤 상태인지 나타낸다. */
public enum PredictionResultStatus {
	HIT,
	MISS,
	REFUNDED
}
