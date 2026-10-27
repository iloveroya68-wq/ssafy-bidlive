package com.ssafy.bidlive.prediction.api.dto;

/** PREDICTION_ACCEPTED가 공개하는 전체 참여자 집계와 안내 문구를 표현한다. */
public record PredictionAcceptedData(
	long participantCount,
	String vibeMessage
) {

	public PredictionAcceptedData {
		if (participantCount < 0L) {
			throw new IllegalArgumentException(
				"participantCount must not be negative"
			);
		}
		if (vibeMessage == null || vibeMessage.isBlank()) {
			throw new IllegalArgumentException("vibeMessage must not be blank");
		}
	}
}
