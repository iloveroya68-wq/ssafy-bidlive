package com.ssafy.bidlive.prediction.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.bidlive.prediction.api.dto.PredictionResultResponse.BandResult;

/** 예측 결과 분포 응답의 중첩 JSON 계약과 불변 밴드 목록을 검증한다. */
class PredictionResultResponseTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void serializesPredictionResultResponseAccordingToApiContract() {
		PredictionResultResponse response = new PredictionResultResponse(
			100L,
			"COMPLETED",
			List.of(
				new BandResult("WITHIN_5", 2L, 5, 2_500L),
				new BandResult("WITHIN_10", 5L, 3, 4_200L),
				new BandResult("WITHIN_20", 3L, new BigDecimal("1.5"), 900L),
				new BandResult("MISS", 8L, 0, 0L)
			)
		);

		JsonNode json = objectMapper.valueToTree(response);

		assertThat(json.get("auctionId").asLong()).isEqualTo(100L);
		assertThat(json.get("settlementStatus").asText())
			.isEqualTo("COMPLETED");
		assertBandResult(
			json.at("/bands/0"),
			"WITHIN_5",
			2L,
			5,
			2_500L
		);
		assertBandResult(
			json.at("/bands/1"),
			"WITHIN_10",
			5L,
			3,
			4_200L
		);
		assertBandResult(
			json.at("/bands/2"),
			"WITHIN_20",
			3L,
			new BigDecimal("1.5"),
			900L
		);
		assertBandResult(json.at("/bands/3"), "MISS", 8L, 0, 0L);
	}

	@Test
	void copiesBandsToKeepResponseImmutable() {
		List<BandResult> bands = new ArrayList<>();
		bands.add(new BandResult("WITHIN_5", 2L, 5, 2_500L));

		PredictionResultResponse response = new PredictionResultResponse(
			100L,
			"COMPLETED",
			bands
		);
		bands.clear();

		assertThat(response.bands()).hasSize(1);
	}

	private static void assertBandResult(
		JsonNode result,
		String band,
		long participantCount,
		int multiplier,
		long totalPayoutAmount
	) {
		assertBandResult(
			result,
			band,
			participantCount,
			BigDecimal.valueOf(multiplier),
			totalPayoutAmount
		);
	}

	private static void assertBandResult(
		JsonNode result,
		String band,
		long participantCount,
		BigDecimal multiplier,
		long totalPayoutAmount
	) {
		assertThat(result.get("band").asText()).isEqualTo(band);
		assertThat(result.get("participantCount").asLong())
			.isEqualTo(participantCount);
		assertThat(result.get("multiplier").decimalValue())
			.isEqualByComparingTo(multiplier);
		assertThat(result.get("totalPayoutAmount").asLong())
			.isEqualTo(totalPayoutAmount);
	}
}
