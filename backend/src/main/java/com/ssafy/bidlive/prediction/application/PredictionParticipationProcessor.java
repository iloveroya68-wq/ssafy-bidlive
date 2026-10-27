package com.ssafy.bidlive.prediction.application;

/** 경매 상태와 기존 참여를 검증하고 예측 참여를 원자적으로 등록하는 계약이다. */
public interface PredictionParticipationProcessor {

	PredictionParticipationResult process(
		PredictionParticipationCommand command
	);
}
