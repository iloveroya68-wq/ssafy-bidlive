package com.ssafy.bidlive.prediction.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/** PREDICTION_SETTLED가 추가 결과 없이 완료 사실만 직렬화하는지 검증한다. */
class PredictionSettledMessageTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void serializesCompletionOnlyMessage() throws Exception {
		JsonNode json = objectMapper.valueToTree(
			PredictionSettledMessage.completed()
		);

		assertThat(json.size()).isEqualTo(2);
		assertThat(json.get("type").asText())
			.isEqualTo("PREDICTION_SETTLED");
		assertThat(json.get("data").isObject()).isTrue();
		assertThat(json.get("data").isEmpty()).isTrue();
	}
}
