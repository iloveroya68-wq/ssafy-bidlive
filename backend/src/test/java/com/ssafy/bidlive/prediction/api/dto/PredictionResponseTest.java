package com.ssafy.bidlive.prediction.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;

/** 낙찰가 예측 접수 응답의 JSON 필드와 시간 형식을 검증합니다. */
class PredictionResponseTest {

	private final JsonMapper jsonMapper = JsonMapper.builder()
		.findAndAddModules()
		.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
		.build();

	@Test
	void serializesPredictionResponseAccordingToApiContract() throws Exception {
		PredictionResponse response = new PredictionResponse(
			701L,
			100L,
			74_000L,
			300L,
			"PLACED",
			OffsetDateTime.parse("2026-07-21T20:08:00+09:00")
		);

		JsonNode json = jsonMapper.valueToTree(response);

		assertThat(json.get("predictionId").asLong()).isEqualTo(701L);
		assertThat(json.get("auctionId").asLong()).isEqualTo(100L);
		assertThat(json.get("predictedPrice").asLong()).isEqualTo(74_000L);
		assertThat(json.get("stakeAmount").asLong()).isEqualTo(300L);
		assertThat(json.get("status").asText()).isEqualTo("PLACED");
		assertThat(json.get("submittedAt").asText()).isEqualTo("2026-07-21T20:08:00+09:00");
	}
}
