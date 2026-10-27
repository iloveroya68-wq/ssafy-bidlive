package com.ssafy.bidlive.prediction.application;

import static com.ssafy.bidlive.prediction.domain.PredictionResultQueryErrorCode.AUCTION_NOT_FOUND;
import static com.ssafy.bidlive.prediction.domain.PredictionResultQueryErrorCode.INVALID_AUCTION_ID;
import static com.ssafy.bidlive.prediction.domain.PredictionResultQueryErrorCode.PREDICTION_RESULT_NOT_AVAILABLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.stream.Stream;

import com.ssafy.bidlive.prediction.domain.PredictionResultQueryErrorCode;
import com.ssafy.bidlive.prediction.domain.PredictionSettlementState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** PRED-02가 종료 및 전체 예측 정산 완료 후에만 공개되는지 검증한다. */
class PredictionResultAvailabilityServiceTest {

	@Mock
	private PredictionResultAvailabilityPersistence persistence;

	private AutoCloseable mocks;
	private PredictionResultAvailabilityService service;

	@BeforeEach
	void setUp() {
		mocks = MockitoAnnotations.openMocks(this);
		service = new PredictionResultAvailabilityService(persistence);
	}

	@ParameterizedTest
	@MethodSource("invalidAuctionIds")
	void rejectsInvalidAuctionId(Long auctionId) {
		assertError(auctionId, INVALID_AUCTION_ID);

		verify(persistence, never()).findStateByAuctionId(auctionId);
	}

	@Test
	void rejectsMissingAuction() {
		when(persistence.findStateByAuctionId(100L))
			.thenReturn(Optional.empty());

		assertError(100L, AUCTION_NOT_FOUND);
	}

	@ParameterizedTest
	@MethodSource("incompleteStates")
	void rejectsUnavailableSettlement(PredictionSettlementState state) {
		when(persistence.findStateByAuctionId(100L))
			.thenReturn(Optional.of(state));

		assertError(100L, PREDICTION_RESULT_NOT_AVAILABLE);
	}

	@Test
	void acceptsEndedAuctionWhenEveryPredictionIsSettled() {
		PredictionSettlementState state =
			new PredictionSettlementState(501L, true, 2L, 2L);
		when(persistence.findStateByAuctionId(100L))
			.thenReturn(Optional.of(state));

		assertThat(service.validateCompleted(100L)).isEqualTo(state);
	}

	@Test
	void acceptsEndedAuctionWithoutPredictionParticipants() {
		PredictionSettlementState state =
			new PredictionSettlementState(501L, true, 0L, 0L);
		when(persistence.findStateByAuctionId(100L))
			.thenReturn(Optional.of(state));

		assertThat(service.validateCompleted(100L)).isEqualTo(state);
	}

	private void assertError(
		Long auctionId,
		PredictionResultQueryErrorCode errorCode
	) {
		assertThatThrownBy(() -> service.validateCompleted(auctionId))
			.isInstanceOfSatisfying(
				PredictionResultQueryException.class,
				exception -> assertThat(exception.getErrorCode())
					.isEqualTo(errorCode)
			);
	}

	private static Stream<Long> invalidAuctionIds() {
		return Stream.of(null, 0L, -1L);
	}

	private static Stream<Arguments> incompleteStates() {
		return Stream.of(
			Arguments.of(new PredictionSettlementState(
				null,
				false,
				1L,
				0L
			)),
			Arguments.of(new PredictionSettlementState(
				null,
				true,
				1L,
				0L
			)),
			Arguments.of(new PredictionSettlementState(
				501L,
				false,
				1L,
				1L
			)),
			Arguments.of(new PredictionSettlementState(
				501L,
				true,
				2L,
				1L
			))
		);
	}
}
