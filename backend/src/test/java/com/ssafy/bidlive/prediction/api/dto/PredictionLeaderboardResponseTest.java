package com.ssafy.bidlive.prediction.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ssafy.bidlive.prediction.api.dto.PredictionLeaderboardResponse.RankEntry;

/** 일일 리더보드 응답의 중첩 JSON 계약과 불변 순위 목록을 검증한다. */
class PredictionLeaderboardResponseTest {

	private final JsonMapper jsonMapper = JsonMapper.builder()
		.findAndAddModules()
		.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
		.build();

	@Test
	void serializesPredictionLeaderboardResponseAccordingToApiContract() {
		PredictionLeaderboardResponse response = new PredictionLeaderboardResponse(
			LocalDate.parse("2026-07-22"),
			OffsetDateTime.parse("2026-07-22T10:35:00+09:00"),
			List.of(new RankEntry(
				1L,
				"시세예언자",
				4,
				2_000L,
				8_000L,
				new BigDecimal("400.0")
			)),
			new RankEntry(
				18L,
				"택림",
				3,
				1_500L,
				3_000L,
				new BigDecimal("200.0")
			),
			"opaque-cursor",
			true
		);

		JsonNode json = jsonMapper.valueToTree(response);

		assertThat(json.get("rankingDate").asText()).isEqualTo("2026-07-22");
		assertThat(json.get("calculatedAt").asText())
			.isEqualTo("2026-07-22T10:35:00+09:00");
		assertRankEntry(json.at("/items/0"), 1L, "시세예언자", 4, 2_000L, 8_000L, "400.0");
		assertRankEntry(json.get("myRank"), 18L, "택림", 3, 1_500L, 3_000L, "200.0");
		assertThat(json.get("nextCursor").asText()).isEqualTo("opaque-cursor");
		assertThat(json.get("hasNext").asBoolean()).isTrue();
	}

	@Test
	void serializesEmptyLeaderboardAccordingToApiContract() {
		PredictionLeaderboardResponse response = new PredictionLeaderboardResponse(
			LocalDate.parse("2026-07-22"),
			null,
			List.of(),
			null,
			null,
			false
		);

		JsonNode json = jsonMapper.valueToTree(response);

		assertThat(json.get("items").isArray()).isTrue();
		assertThat(json.get("calculatedAt").isNull()).isTrue();
		assertThat(json.get("items")).isEmpty();
		assertThat(json.get("myRank").isNull()).isTrue();
		assertThat(json.get("nextCursor").isNull()).isTrue();
		assertThat(json.get("hasNext").asBoolean()).isFalse();
	}

	@Test
	void copiesItemsToKeepResponseImmutable() {
		List<RankEntry> items = new ArrayList<>();
		items.add(new RankEntry(
			1L,
			"시세예언자",
			4,
			2_000L,
			8_000L,
			new BigDecimal("400.0")
		));

		PredictionLeaderboardResponse response = new PredictionLeaderboardResponse(
			LocalDate.parse("2026-07-22"),
			OffsetDateTime.parse("2026-07-22T10:35:00+09:00"),
			items,
			null,
			null,
			false
		);
		items.clear();

		assertThat(response.items()).hasSize(1);
	}

	private static void assertRankEntry(
		JsonNode entry,
		long rank,
		String nickname,
		int settledPredictionCount,
		long totalStakedPoints,
		long totalPayoutPoints,
		String payoutRate
	) {
		assertThat(entry.get("rank").asLong()).isEqualTo(rank);
		assertThat(entry.get("nickname").asText()).isEqualTo(nickname);
		assertThat(entry.get("settledPredictionCount").asInt())
			.isEqualTo(settledPredictionCount);
		assertThat(entry.get("totalStakedPoints").asLong())
			.isEqualTo(totalStakedPoints);
		assertThat(entry.get("totalPayoutPoints").asLong())
			.isEqualTo(totalPayoutPoints);
		assertThat(entry.get("payoutRate").decimalValue())
			.isEqualByComparingTo(payoutRate);
	}
}
