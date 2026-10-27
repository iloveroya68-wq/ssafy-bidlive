package com.ssafy.bidlive.prediction.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import com.ssafy.bidlive.prediction.application.PredictionAchievementResult;
import org.junit.jupiter.api.Test;

/** Application 조회 결과가 PRED-04 API 응답 계약으로 변환되는지 검증한다. */
class PredictionAchievementResponseMappingTest {

	@Test
	void mapsApplicationResultToApiResponse() {
		OffsetDateTime earnedAt = OffsetDateTime.parse(
			"2026-07-20T21:30:00+09:00"
		);
		PredictionAchievementResult result = new PredictionAchievementResult(
			25L,
			18,
			7,
			new BigDecimal("38.9"),
			9_200L,
			13_400L,
			new PredictionAchievementResult.Grade(
				"ORACLE_2",
				"시세 예언자 II",
				720L
			),
			List.of(new PredictionAchievementResult.Badge(
				"FIRST_HIT",
				"첫 적중",
				earnedAt
			))
		);

		PredictionAchievementResponse response =
			PredictionAchievementResponse.from(result);

		assertThat(response.memberId()).isEqualTo(25L);
		assertThat(response.predictionCount()).isEqualTo(18);
		assertThat(response.hitCount()).isEqualTo(7);
		assertThat(response.hitRate()).isEqualByComparingTo("38.9");
		assertThat(response.totalStakedPoints()).isEqualTo(9_200L);
		assertThat(response.totalRewardPoints()).isEqualTo(13_400L);
		assertThat(response.grade()).isEqualTo(
			new PredictionAchievementResponse.Grade(
				"ORACLE_2",
				"시세 예언자 II",
				720L
			)
		);
		assertThat(response.badges()).containsExactly(
			new PredictionAchievementResponse.Badge(
				"FIRST_HIT",
				"첫 적중",
				earnedAt
			)
		);
	}
}
