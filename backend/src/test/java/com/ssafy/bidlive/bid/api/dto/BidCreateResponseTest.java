package com.ssafy.bidlive.bid.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;

/** 입찰 접수 응답의 공개 필드와 시간 형식을 검증한다. */
class BidCreateResponseTest {

	private final JsonMapper jsonMapper = JsonMapper.builder()
		.findAndAddModules()
		.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
		.build();

	@Test
	void serializesBidCreateResponseAccordingToApiContract() {
		BidCreateResponse response = new BidCreateResponse(
			100L,
			82_000L,
			70_000L,
			12_000L,
			82_000L,
			118_000L,
			OffsetDateTime.parse("2026-07-20T21:12:44+09:00")
		);

		JsonNode json = jsonMapper.valueToTree(response);

		assertThat(json.properties())
			.extracting(java.util.Map.Entry::getKey)
			.containsExactlyInAnyOrderElementsOf(Set.of(
				"auctionId",
				"myBidAmount",
				"previousBidAmount",
				"additionalHoldAmount",
				"totalHeldAmount",
				"availableBalance",
				"submittedAt"
			));
		assertThat(json.get("auctionId").asLong()).isEqualTo(100L);
		assertThat(json.get("myBidAmount").asLong()).isEqualTo(82_000L);
		assertThat(json.get("previousBidAmount").asLong()).isEqualTo(70_000L);
		assertThat(json.get("additionalHoldAmount").asLong()).isEqualTo(12_000L);
		assertThat(json.get("totalHeldAmount").asLong()).isEqualTo(82_000L);
		assertThat(json.get("availableBalance").asLong()).isEqualTo(118_000L);
		assertThat(json.get("submittedAt").asText()).isEqualTo("2026-07-20T21:12:44+09:00");
	}
}
