package com.ssafy.bidlive.common.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;

/**
 * WebSocket 공통 envelope이 프로젝트 Jackson 설정에서 `type`·`data` 두 필드로만 직렬화되고
 * 시각 필드가 ISO-8601 오프셋 문자열로 나가는지 검증합니다.
 */
@JsonTest
class WsEventTest {

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void serializesToTypeAndDataOnly() throws Exception {
		WsEvent<SampleData> event = new WsEvent<>("SAMPLE_EVENT", new SampleData(7));

		JsonNode json = objectMapper.valueToTree(event);

		assertThat(json.properties()).extracting(entry -> entry.getKey())
			.containsExactlyInAnyOrder("type", "data");
		assertThat(json.get("type").asText()).isEqualTo("SAMPLE_EVENT");
		assertThat(json.get("data").get("count").asInt()).isEqualTo(7);
	}

	/** 명세 예시가 모두 오프셋 포함 문자열이므로 epoch 숫자로 직렬화되면 계약이 깨진다. */
	@Test
	void serializesDateTimeAsIsoOffsetString() throws Exception {
		OffsetDateTime endedAt = OffsetDateTime.parse("2026-07-20T21:40:00+09:00");
		WsEvent<TimestampedData> event = new WsEvent<>("SAMPLE_EVENT", new TimestampedData(endedAt));

		String json = objectMapper.writeValueAsString(event);

		assertThat(json).isEqualTo(
			"{\"type\":\"SAMPLE_EVENT\",\"data\":{\"endedAt\":\"2026-07-20T21:40:00+09:00\"}}");
	}

	@Test
	void rejectsBlankTypeAndNullData() {
		assertThatThrownBy(() -> new WsEvent<>(" ", new SampleData(1)))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new WsEvent<>("SAMPLE_EVENT", null))
			.isInstanceOf(IllegalArgumentException.class);
	}

	private record SampleData(int count) {
	}

	private record TimestampedData(OffsetDateTime endedAt) {
	}
}
