package com.ssafy.bidlive.prediction.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/** 예측 참여 명령이 필수 식별자와 서버 접수 시각을 보장하는지 검증한다. */
class PredictionParticipationCommandTest {

	private static final OffsetDateTime REQUESTED_AT =
		OffsetDateTime.parse("2026-07-29T21:00:00+09:00");
	private static final UUID CLIENT_REQUEST_ID =
		UUID.fromString("2b8bb687-6d5e-4e09-b651-b88727266a51");

	@Test
	void rejectsInvalidInternalCommandValues() {
		assertThatThrownBy(() ->
			new PredictionParticipationCommand(
				0L, 1L, 74_000L, 300L, CLIENT_REQUEST_ID, REQUESTED_AT
			)
		).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() ->
			new PredictionParticipationCommand(
				1L, 0L, 74_000L, 300L, CLIENT_REQUEST_ID, REQUESTED_AT
			)
		).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() ->
			new PredictionParticipationCommand(
				1L, 1L, 0L, 300L, CLIENT_REQUEST_ID, REQUESTED_AT
			)
		).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() ->
			new PredictionParticipationCommand(
				1L, 1L, 74_000L, 0L, CLIENT_REQUEST_ID, REQUESTED_AT
			)
		).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() ->
			new PredictionParticipationCommand(
				1L, 1L, 74_000L, 300L, null, REQUESTED_AT
			)
		).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() ->
			new PredictionParticipationCommand(
				1L, 1L, 74_000L, 300L, CLIENT_REQUEST_ID, null
			)
		).isInstanceOf(IllegalArgumentException.class);
	}
}
