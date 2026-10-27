package com.ssafy.bidlive.prediction.api.dto;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** 예측 성공 WebSocket payload가 공개 집계와 안내 문구의 유효성을 지키는지 검증한다. */
class PredictionAcceptedDataTest {

	@Test
	void rejectsInvalidPublicData() {
		assertThatThrownBy(() -> new PredictionAcceptedData(-1L, "예측 참여"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("participantCount must not be negative");
		assertThatThrownBy(() -> new PredictionAcceptedData(1L, " "))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("vibeMessage must not be blank");
	}
}
