package com.ssafy.bidlive.prediction.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

/** PRED-05 상위 10명 캐시 Key가 날짜별 계약을 따르는지 검증한다. */
class PredictionLeaderboardRedisKeyTest {

	@Test
	void createsDailyTop10Key() {
		assertThat(PredictionLeaderboardRedisKey.top10(
			LocalDate.parse("2026-07-22")
		)).isEqualTo(
			"prediction:leaderboard:daily:2026-07-22:top10"
		);
	}
}
