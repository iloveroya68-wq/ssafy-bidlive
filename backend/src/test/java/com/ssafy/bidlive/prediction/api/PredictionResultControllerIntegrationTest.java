package com.ssafy.bidlive.prediction.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.stream.Stream;
import java.math.BigDecimal;

import com.ssafy.bidlive.prediction.application.PredictionResultAggregationPersistence.BandAggregate;
import com.ssafy.bidlive.prediction.application.PredictionResultQueryException;
import com.ssafy.bidlive.prediction.application.PredictionResultQueryResult;
import com.ssafy.bidlive.prediction.application.PredictionResultQueryService;
import com.ssafy.bidlive.prediction.domain.PredictionResultQueryErrorCode;
import com.ssafy.bidlive.prediction.domain.PredictionSettlementBand;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 실제 Security 필터 체인에서 PRED-02 공개 조회와 오류 응답 계약을 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
class PredictionResultControllerIntegrationTest {

	private static final String PATH =
		"/api/v1/auctions/100/prediction-results";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private PredictionResultQueryService queryService;

	@Test
	void returnsCompletedPredictionResultsWithoutAuthentication()
		throws Exception {
		when(queryService.getPredictionResult(100L))
			.thenReturn(new PredictionResultQueryResult(
				100L,
				List.of(
					new BandAggregate(
						PredictionSettlementBand.WITHIN_5,
						2L,
						5,
						2_500L
					),
					new BandAggregate(
						PredictionSettlementBand.WITHIN_10,
						5L,
						3,
						4_200L
					),
					new BandAggregate(
						PredictionSettlementBand.WITHIN_20,
						3L,
						new BigDecimal("1.5"),
						900L
					),
					new BandAggregate(
						PredictionSettlementBand.MISS,
						8L,
						0,
						0L
					)
				)
			));

		mockMvc.perform(get(PATH))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.auctionId").value(100L))
			.andExpect(jsonPath("$.settlementStatus")
				.value("COMPLETED"))
			.andExpect(jsonPath("$.bands[0].band")
				.value("WITHIN_5"))
			.andExpect(jsonPath("$.bands[0].participantCount")
				.value(2L))
			.andExpect(jsonPath("$.bands[0].multiplier").value(5))
			.andExpect(jsonPath("$.bands[0].totalPayoutAmount")
				.value(2_500L))
			.andExpect(jsonPath("$.bands[3].band").value("MISS"));
	}

	@ParameterizedTest
	@MethodSource("queryFailureCases")
	void mapsPredictionResultFailureToApiError(
		PredictionResultQueryErrorCode errorCode,
		int expectedStatus
	) throws Exception {
		when(queryService.getPredictionResult(100L))
			.thenThrow(new PredictionResultQueryException(errorCode));

		mockMvc.perform(get(PATH))
			.andExpect(status().is(expectedStatus))
			.andExpect(jsonPath("$.code").value(errorCode.name()));
	}

	private static Stream<Arguments> queryFailureCases() {
		return Stream.of(
			Arguments.of(
				PredictionResultQueryErrorCode.INVALID_AUCTION_ID,
				400
			),
			Arguments.of(
				PredictionResultQueryErrorCode.AUCTION_NOT_FOUND,
				404
			),
			Arguments.of(
				PredictionResultQueryErrorCode
					.PREDICTION_RESULT_NOT_AVAILABLE,
				409
			)
		);
	}
}
