package com.ssafy.bidlive.prediction.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;

/** 일일 리더보드 커서가 날짜·집계 버전·마지막 순위를 안전하게 왕복하는지 검증한다. */
class PredictionLeaderboardCursorTest {

	@Test
	void roundTripsOpaqueCursor() {
		PredictionLeaderboardCursor cursor = new PredictionLeaderboardCursor(
			LocalDate.parse("2026-07-22"),
			OffsetDateTime.parse("2026-07-23T00:05:03+09:00"),
			10L,
			25L
		);

		assertThat(PredictionLeaderboardCursor.decode(cursor.encode()))
			.isEqualTo(cursor);
	}

	@Test
	void rejectsMalformedCursor() {
		assertThatThrownBy(() ->
			PredictionLeaderboardCursor.decode("not*base64*")
		).isInstanceOf(IllegalArgumentException.class);
	}
}
