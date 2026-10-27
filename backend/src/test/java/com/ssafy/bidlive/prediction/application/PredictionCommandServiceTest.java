package com.ssafy.bidlive.prediction.application;

import static com.ssafy.bidlive.prediction.domain.PredictionParticipationFailureCode.AUCTION_NOT_LIVE;
import static com.ssafy.bidlive.prediction.domain.PredictionParticipationFailureCode.IDEMPOTENCY_KEY_REUSED;
import static com.ssafy.bidlive.prediction.domain.PredictionParticipationFailureCode.INSUFFICIENT_BETTING_POINTS;
import static com.ssafy.bidlive.prediction.domain.PredictionParticipationFailureCode.PREDICTION_ALREADY_EXISTS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import com.ssafy.bidlive.prediction.application.PredictionParticipationPersistence.NewPrediction;
import com.ssafy.bidlive.prediction.application.PredictionParticipationPersistence.StoredPrediction;
import com.ssafy.bidlive.wallet.application.BettingPointBetCommand;
import com.ssafy.bidlive.wallet.application.BettingPointBetErrorCode;
import com.ssafy.bidlive.wallet.application.BettingPointBetException;
import com.ssafy.bidlive.wallet.application.BettingPointBetResult;
import com.ssafy.bidlive.wallet.application.BettingPointBetService;
import com.ssafy.bidlive.wallet.application.BettingWalletRealtimeInitializationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/** PRED-01의 저장·BET 차감과 신규 참여 이벤트 발행 경계를 검증한다. */
@ExtendWith(MockitoExtension.class)
class PredictionCommandServiceTest {

	private static final UUID CLIENT_REQUEST_ID =
		UUID.fromString("2b8bb687-6d5e-4e09-b651-b88727266a51");
	private static final OffsetDateTime SUBMITTED_AT =
		OffsetDateTime.parse("2026-07-21T20:08:00+09:00");

	@Mock
	private PredictionParticipationProcessor participationProcessor;

	@Mock
	private PredictionParticipationPersistence persistence;

	@Mock
	private BettingPointBetService bettingPointBetService;

	@Mock
	private BettingWalletRealtimeInitializationService
		bettingWalletRealtimeInitializationService;

	@Mock
	private ApplicationEventPublisher eventPublisher;

	private PredictionCommandService service;

	@BeforeEach
	void setUp() {
		Clock clock = Clock.fixed(
			Instant.parse("2026-07-21T11:08:00Z"),
			ZoneId.of("Asia/Seoul")
		);
		service = new PredictionCommandService(
			participationProcessor,
			persistence,
			bettingPointBetService,
			bettingWalletRealtimeInitializationService,
			eventPublisher,
			clock
		);
	}

	@Test
	void storesAcceptedPredictionAndDeductsBettingPoints() {
		when(persistence.findByIdempotencyKey(CLIENT_REQUEST_ID.toString()))
			.thenReturn(Optional.empty());
		when(persistence.findByAuctionIdAndMemberId(100L, 25L))
			.thenReturn(Optional.empty());
		when(participationProcessor.process(any()))
			.thenReturn(PredictionParticipationResult.accepted(
				SUBMITTED_AT,
				3L,
				1_700L,
				false
			));
		when(persistence.save(any(NewPrediction.class)))
			.thenReturn(storedPrediction());
		when(bettingPointBetService.deduct(any()))
			.thenReturn(new BettingPointBetResult(
				10L,
				1001L,
				701L,
				300L,
				1_700L,
				false
			));

		PredictionCreateResult result = create();

		assertThat(result).isEqualTo(new PredictionCreateResult(
			701L,
			100L,
			74_000L,
			300L,
			SUBMITTED_AT
		));
		verify(persistence).lockIdempotencyKey(
			CLIENT_REQUEST_ID.toString()
		);
		ArgumentCaptor<NewPrediction> predictionCaptor =
			ArgumentCaptor.forClass(NewPrediction.class);
		verify(persistence).save(predictionCaptor.capture());
		assertThat(predictionCaptor.getValue()).isEqualTo(new NewPrediction(
			100L,
			25L,
			74_000L,
			300L,
			CLIENT_REQUEST_ID.toString(),
			SUBMITTED_AT
		));
		verify(bettingPointBetService).deduct(new BettingPointBetCommand(
			25L,
			701L,
			300L,
			"PREDICTION_BET:" + CLIENT_REQUEST_ID
		));
		InOrder order = inOrder(
			bettingWalletRealtimeInitializationService,
			participationProcessor
		);
		order.verify(bettingWalletRealtimeInitializationService)
			.initializeIfAbsent(25L);
		order.verify(participationProcessor).process(any());
		verify(eventPublisher).publishEvent(
			new PredictionAcceptedEvent(100L, 3L)
		);
	}

	@Test
	void returnsStoredPredictionForSameIdempotentRequest() {
		when(persistence.findByIdempotencyKey(CLIENT_REQUEST_ID.toString()))
			.thenReturn(Optional.of(storedPrediction()));

		PredictionCreateResult result = create();

		assertThat(result.predictionId()).isEqualTo(701L);
		verify(participationProcessor, never()).process(any());
		verify(bettingWalletRealtimeInitializationService, never())
			.initializeIfAbsent(any());
		verify(persistence, never()).save(any());
		verify(bettingPointBetService, never()).deduct(any());
		verify(eventPublisher, never()).publishEvent(any());
	}

	@Test
	void rejectsIdempotencyKeyReusedWithDifferentStake() {
		StoredPrediction existing = new StoredPrediction(
			701L,
			100L,
			25L,
			74_000L,
			200L,
			CLIENT_REQUEST_ID.toString(),
			SUBMITTED_AT
		);
		when(persistence.findByIdempotencyKey(CLIENT_REQUEST_ID.toString()))
			.thenReturn(Optional.of(existing));

		assertRejected(() -> create(), IDEMPOTENCY_KEY_REUSED);
		verify(participationProcessor, never()).process(any());
		verify(bettingPointBetService, never()).deduct(any());
	}

	@Test
	void rejectsAnotherPredictionForSameAuctionAndMember() {
		when(persistence.findByIdempotencyKey(CLIENT_REQUEST_ID.toString()))
			.thenReturn(Optional.empty());
		when(persistence.findByAuctionIdAndMemberId(100L, 25L))
			.thenReturn(Optional.of(storedPrediction()));

		assertRejected(() -> create(), PREDICTION_ALREADY_EXISTS);
		verify(participationProcessor, never()).process(any());
		verify(bettingWalletRealtimeInitializationService, never())
			.initializeIfAbsent(any());
		verify(bettingPointBetService, never()).deduct(any());
	}

	@Test
	void propagatesRedisRejectionWithoutDatabaseWrite() {
		when(persistence.findByIdempotencyKey(CLIENT_REQUEST_ID.toString()))
			.thenReturn(Optional.empty());
		when(persistence.findByAuctionIdAndMemberId(100L, 25L))
			.thenReturn(Optional.empty());
		when(participationProcessor.process(any()))
			.thenReturn(PredictionParticipationResult.rejected(
				AUCTION_NOT_LIVE
			));

		assertRejected(() -> create(), AUCTION_NOT_LIVE);
		verify(persistence, never()).save(any());
		verify(bettingPointBetService, never()).deduct(any());
	}

	@Test
	void mapsWalletInsufficientBalanceToPredictionFailure() {
		when(persistence.findByIdempotencyKey(CLIENT_REQUEST_ID.toString()))
			.thenReturn(Optional.empty());
		when(persistence.findByAuctionIdAndMemberId(100L, 25L))
			.thenReturn(Optional.empty());
		when(participationProcessor.process(any()))
			.thenReturn(PredictionParticipationResult.accepted(
				SUBMITTED_AT,
				3L,
				1_700L,
				false
			));
		when(persistence.save(any(NewPrediction.class)))
			.thenReturn(storedPrediction());
		when(bettingPointBetService.deduct(any()))
			.thenThrow(new BettingPointBetException(
				BettingPointBetErrorCode.INSUFFICIENT_BETTING_POINTS
			));

		assertRejected(() -> create(), INSUFFICIENT_BETTING_POINTS);
		verify(eventPublisher, never()).publishEvent(any());
	}

	private PredictionCreateResult create() {
		return service.createPrediction(
			100L,
			25L,
			74_000L,
			300L,
			CLIENT_REQUEST_ID
		);
	}

	private StoredPrediction storedPrediction() {
		return new StoredPrediction(
			701L,
			100L,
			25L,
			74_000L,
			300L,
			CLIENT_REQUEST_ID.toString(),
			SUBMITTED_AT
		);
	}

	private void assertRejected(
		org.assertj.core.api.ThrowableAssert.ThrowingCallable callable,
		com.ssafy.bidlive.prediction.domain.PredictionParticipationFailureCode expected
	) {
		assertThatThrownBy(callable)
			.isInstanceOfSatisfying(
				PredictionRejectedException.class,
				exception -> assertThat(exception.getFailureCode())
					.isEqualTo(expected)
			);
	}
}
