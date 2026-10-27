package com.ssafy.bidlive.prediction.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ssafy.bidlive.prediction.api.dto.PredictionAchievementResponse.Badge;
import com.ssafy.bidlive.prediction.api.dto.PredictionAchievementResponse.Grade;

/** 예측 성과 응답의 중첩 JSON 계약과 불변 뱃지 목록을 검증합니다. */
class PredictionAchievementResponseTest {

	private final JsonMapper jsonMapper = JsonMapper.builder()
		.findAndAddModules()
		.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
		.build();

	@Test
	void serializesPredictionAchievementResponseAccordingToApiContract() {
		PredictionAchievementResponse response = new PredictionAchievementResponse(
			25L,
			18,
			7,
			new BigDecimal("38.9"),
			9_200L,
			13_400L,
			new Grade("ORACLE_2", "시세 예언자 II", 720L),
			List.of(new Badge(
				"FIRST_HIT",
				"첫 적중",
				OffsetDateTime.parse("2026-07-20T21:30:00+09:00")
			))
		);

		JsonNode json = jsonMapper.valueToTree(response);

		assertThat(json.get("memberId").asLong()).isEqualTo(25L);
		assertThat(json.get("predictionCount").asInt()).isEqualTo(18);
		assertThat(json.get("hitCount").asInt()).isEqualTo(7);
		assertThat(json.get("hitRate").decimalValue()).isEqualByComparingTo("38.9");
		assertThat(json.get("totalStakedPoints").asLong()).isEqualTo(9_200L);
		assertThat(json.get("totalRewardPoints").asLong()).isEqualTo(13_400L);
		assertThat(json.at("/grade/code").asText()).isEqualTo("ORACLE_2");
		assertThat(json.at("/grade/name").asText()).isEqualTo("시세 예언자 II");
		assertThat(json.at("/grade/score").asLong()).isEqualTo(720L);
		assertThat(json.at("/badges/0/badgeCode").asText()).isEqualTo("FIRST_HIT");
		assertThat(json.at("/badges/0/name").asText()).isEqualTo("첫 적중");
		assertThat(json.at("/badges/0/earnedAt").asText())
			.isEqualTo("2026-07-20T21:30:00+09:00");
	}

	@Test
	void copiesBadgesToKeepResponseImmutable() {
		List<Badge> badges = new ArrayList<>();
		badges.add(new Badge(
			"FIRST_HIT",
			"첫 적중",
			OffsetDateTime.parse("2026-07-20T21:30:00+09:00")
		));

		PredictionAchievementResponse response = new PredictionAchievementResponse(
			25L,
			1,
			1,
			new BigDecimal("100.0"),
			300L,
			1_500L,
			new Grade("ORACLE_1", "시세 예언자 I", 100L),
			badges
		);

		badges.clear();

		assertThat(response.badges()).hasSize(1);
	}
}
