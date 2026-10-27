package com.ssafy.bidlive.auction.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;

/** 판매자 방송 연장 응답의 공개 필드와 오프셋 시간 형식을 검증한다. */
class AuctionExtensionResponseTest {

	private final JsonMapper jsonMapper = JsonMapper.builder()
		.findAndAddModules()
		.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
		.build();

	@Test
	void serializesAuctionExtensionResponseAccordingToApiContract() {
		AuctionExtensionResponse response = new AuctionExtensionResponse(
			100L,
			OffsetDateTime.parse("2026-07-20T21:20:00+09:00"),
			OffsetDateTime.parse("2026-07-20T21:25:00+09:00"),
			2,
			1
		);

		JsonNode json = jsonMapper.valueToTree(response);

		assertThat(json.properties())
			.extracting(java.util.Map.Entry::getKey)
			.containsExactlyInAnyOrderElementsOf(Set.of(
				"auctionId",
				"previousEndAt",
				"endAt",
				"extensionCount",
				"remainingExtensions"
			));
		assertThat(json.get("auctionId").asLong()).isEqualTo(100L);
		assertThat(json.get("previousEndAt").asText()).isEqualTo("2026-07-20T21:20:00+09:00");
		assertThat(json.get("endAt").asText()).isEqualTo("2026-07-20T21:25:00+09:00");
		assertThat(json.get("extensionCount").asInt()).isEqualTo(2);
		assertThat(json.get("remainingExtensions").asInt()).isEqualTo(1);
	}
}
