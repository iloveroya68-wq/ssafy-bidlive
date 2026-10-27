package com.ssafy.bidlive.prediction.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import com.ssafy.bidlive.prediction.application.PredictionResultAggregationPersistence.BandAggregate;
import com.ssafy.bidlive.prediction.domain.PredictionSettlementBand;
import com.ssafy.bidlive.prediction.domain.PredictionSettlementState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** PRED-02 조회가 공개 가능 상태를 검증하고 해당 정산 결과를 집계하는지 검증한다. */
class PredictionResultQueryServiceTest {

	@Mock
	private PredictionResultAvailabilityService availabilityService;

	@Mock
	private PredictionResultAggregationPersistence aggregationPersistence;

	private PredictionResultQueryService service;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		service = new PredictionResultQueryService(
			availabilityService,
			aggregationPersistence
		);
	}

	@Test
	void returnsBandAggregatesForCompletedAuction() {
		PredictionSettlementState state =
			new PredictionSettlementState(501L, true, 2L, 2L);
		List<BandAggregate> bands = List.of(
			new BandAggregate(
				PredictionSettlementBand.WITHIN_5,
				2L,
				5,
				2_500L
			)
		);
		when(availabilityService.validateCompleted(100L))
			.thenReturn(state);
		when(aggregationPersistence.findBandAggregatesByAuctionResultId(
			501L
		)).thenReturn(bands);

		PredictionResultQueryResult result =
			service.getPredictionResult(100L);

		assertThat(result.auctionId()).isEqualTo(100L);
		assertThat(result.bands()).containsExactlyElementsOf(bands);
		verify(availabilityService).validateCompleted(100L);
		verify(aggregationPersistence)
			.findBandAggregatesByAuctionResultId(501L);
	}
}
