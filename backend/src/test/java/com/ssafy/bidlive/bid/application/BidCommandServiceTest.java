package com.ssafy.bidlive.bid.application;

import static com.ssafy.bidlive.bid.domain.BidFailureCode.AUCTION_NOT_LIVE;
import static com.ssafy.bidlive.bid.domain.BidFailureCode.IDEMPOTENCY_KEY_REUSED;
import static com.ssafy.bidlive.bid.domain.BidFailureCode.INSUFFICIENT_AVAILABLE_BALANCE;
import static com.ssafy.bidlive.bid.domain.BidFailureCode.PREDICTION_ALREADY_JOINED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import com.ssafy.bidlive.bid.application.BidPersistence.NewBid;
import com.ssafy.bidlive.bid.application.BidPersistence.StoredBid;
import com.ssafy.bidlive.wallet.application.TradePointOperationErrorCode;
import com.ssafy.bidlive.wallet.application.TradePointOperationException;
import com.ssafy.bidlive.wallet.application.TradePointOperationResult;
import com.ssafy.bidlive.wallet.application.TradePointOperationService;
import com.ssafy.bidlive.wallet.application.TradeWalletRealtimeInitializationService;
import com.ssafy.bidlive.wallet.application.WalletQueryResult;
import com.ssafy.bidlive.wallet.application.WalletQueryResult.WalletSummary;
import com.ssafy.bidlive.wallet.application.WalletQueryService;
import com.ssafy.bidlive.wallet.domain.PointLedgerReferenceType;
import com.ssafy.bidlive.wallet.domain.TradePointOperationType;
import com.ssafy.bidlive.wallet.domain.WalletType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/** BID-06 승인 입찰의 저장·홀드와 공개 집계 이벤트 발행을 검증한다. */
@ExtendWith(MockitoExtension.class)
class BidCommandServiceTest {

	private static final OffsetDateTime REQUESTED_AT =
		OffsetDateTime.parse("2026-07-20T21:12:44+09:00");
	private static final UUID IDEMPOTENCY_KEY =
		UUID.fromString("9a61c1e4-7ad4-4e7c-83b5-a63edc00ac20");

	@Mock
	private BidProcessor bidProcessor;

	@Mock
	private BidPersistence persistence;

	@Mock
	private TradePointOperationService tradePointOperationService;

	@Mock
	private TradeWalletRealtimeInitializationService
		tradeWalletRealtimeInitializationService;

	@Mock
	private WalletQueryService walletQueryService;

	@Mock
	private ApplicationEventPublisher eventPublisher;

	private BidCommandService service;

	@BeforeEach
	void setUp() {
		Clock clock = Clock.fixed(
			REQUESTED_AT.toInstant(),
			ZoneOffset.ofHours(9)
		);
		service = new BidCommandService(
			bidProcessor,
			persistence,
			tradePointOperationService,
			tradeWalletRealtimeInitializationService,
			walletQueryService,
			eventPublisher,
			clock
		);
	}

	@Test
	void storesAcceptedBidAndHoldsOnlyAdditionalTradePoints() {
		stubNewRequest();
		when(bidProcessor.process(any()))
			.thenReturn(acceptedProcessing());
		when(persistence.save(any(NewBid.class)))
			.thenReturn(storedBid());
		when(tradePointOperationService.hold(
			PointLedgerReferenceType.BID,
			351L,
			25L,
			12_000L,
			"BID_HOLD:" + IDEMPOTENCY_KEY
		)).thenReturn(walletHold());

		BidResult result = create(82_000L);

		assertThat(result).isEqualTo(new BidResult(
			100L,
			82_000L,
			70_000L,
			12_000L,
			82_000L,
			118_000L,
			REQUESTED_AT
		));
		InOrder realtimeOrder = inOrder(
			tradeWalletRealtimeInitializationService,
			bidProcessor
		);
		realtimeOrder.verify(tradeWalletRealtimeInitializationService)
			.initializeIfAbsent(25L);
		realtimeOrder.verify(bidProcessor).process(any());
		verify(persistence).lockIdempotencyKey(IDEMPOTENCY_KEY.toString());
		verify(persistence).lockParticipation(100L, 25L);
		ArgumentCaptor<NewBid> bidCaptor =
			ArgumentCaptor.forClass(NewBid.class);
		verify(persistence).save(bidCaptor.capture());
		assertThat(bidCaptor.getValue()).isEqualTo(new NewBid(
			100L,
			25L,
			82_000L,
			IDEMPOTENCY_KEY.toString(),
			REQUESTED_AT
		));
		ArgumentCaptor<BidAcceptedEvent> eventCaptor =
			ArgumentCaptor.forClass(BidAcceptedEvent.class);
		verify(eventPublisher).publishEvent(eventCaptor.capture());
		assertThat(eventCaptor.getValue()).isEqualTo(
			new BidAcceptedEvent(100L, 13L, 9L)
		);
	}

	@Test
	void returnsStoredBidWithoutRepeatingRedisOrWalletMutation() {
		when(persistence.findByIdempotencyKey(IDEMPOTENCY_KEY.toString()))
			.thenReturn(Optional.of(storedBid()));
		when(walletQueryService.query(25L)).thenReturn(walletQuery());

		BidResult result = create(82_000L);

		assertThat(result.submittedAt()).isEqualTo(REQUESTED_AT);
		assertThat(result.myBidAmount()).isEqualTo(82_000L);
		verify(bidProcessor, never()).process(any());
		verify(tradeWalletRealtimeInitializationService, never())
			.initializeIfAbsent(any());
		verify(persistence, never()).save(any());
		verify(tradePointOperationService, never()).hold(
			any(), any(), any(), org.mockito.ArgumentMatchers.anyLong(), any()
		);
	}

	@Test
	void rejectsIdempotencyKeyReusedWithDifferentAmount() {
		when(persistence.findByIdempotencyKey(IDEMPOTENCY_KEY.toString()))
			.thenReturn(Optional.of(storedBid()));

		assertRejected(() -> create(83_000L), IDEMPOTENCY_KEY_REUSED);
		verify(bidProcessor, never()).process(any());
		verify(tradeWalletRealtimeInitializationService, never())
			.initializeIfAbsent(any());
	}

	@Test
	void rejectsStoredPredictionBeforeRedisMutation() {
		when(persistence.findByIdempotencyKey(IDEMPOTENCY_KEY.toString()))
			.thenReturn(Optional.empty());
		when(persistence.hasPredictionParticipation(100L, 25L))
			.thenReturn(true);

		assertRejected(() -> create(82_000L), PREDICTION_ALREADY_JOINED);
		verify(bidProcessor, never()).process(any());
		verify(tradeWalletRealtimeInitializationService, never())
			.initializeIfAbsent(any());
	}

	@Test
	void propagatesRedisRejectionWithoutDatabaseWrite() {
		stubNewRequest();
		when(bidProcessor.process(any()))
			.thenReturn(BidProcessingResult.rejected(AUCTION_NOT_LIVE));

		assertRejected(() -> create(82_000L), AUCTION_NOT_LIVE);
		verify(persistence, never()).save(any());
		verify(tradePointOperationService, never()).hold(
			any(), any(), any(), org.mockito.ArgumentMatchers.anyLong(), any()
		);
	}

	@Test
	void mapsDatabaseWalletInsufficientBalanceToBidFailure() {
		stubNewRequest();
		when(bidProcessor.process(any()))
			.thenReturn(acceptedProcessing());
		when(persistence.save(any(NewBid.class)))
			.thenReturn(storedBid());
		when(tradePointOperationService.hold(
			any(), any(), any(), org.mockito.ArgumentMatchers.anyLong(), any()
		)).thenThrow(new TradePointOperationException(
			TradePointOperationErrorCode.INSUFFICIENT_AVAILABLE_BALANCE
		));

		assertRejected(
			() -> create(82_000L),
			INSUFFICIENT_AVAILABLE_BALANCE
		);
	}

	@Test
	void failsWhenRedisAndPostgresPreviousAmountsDiffer() {
		stubNewRequest();
		when(bidProcessor.process(any()))
			.thenReturn(acceptedProcessing());
		when(persistence.save(any(NewBid.class))).thenReturn(new StoredBid(
			351L,
			100L,
			25L,
			82_000L,
			60_000L,
			2,
			IDEMPOTENCY_KEY.toString(),
			REQUESTED_AT
		));

		assertThatThrownBy(() -> create(82_000L))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("previous bid amounts do not match");
		verify(tradePointOperationService, never()).hold(
			any(), any(), any(), org.mockito.ArgumentMatchers.anyLong(), any()
		);
	}

	private void stubNewRequest() {
		when(persistence.findByIdempotencyKey(IDEMPOTENCY_KEY.toString()))
			.thenReturn(Optional.empty());
		when(persistence.hasPredictionParticipation(100L, 25L))
			.thenReturn(false);
	}

	private BidResult create(long amount) {
		return service.createBid(100L, 25L, amount, IDEMPOTENCY_KEY);
	}

	private BidProcessingResult acceptedProcessing() {
		return BidProcessingResult.accepted(
			70_000L,
			82_000L,
			12_000L,
			82_000L,
			118_000L,
			REQUESTED_AT,
			13L,
			9L
		);
	}

	private StoredBid storedBid() {
		return new StoredBid(
			351L,
			100L,
			25L,
			82_000L,
			70_000L,
			2,
			IDEMPOTENCY_KEY.toString(),
			REQUESTED_AT
		);
	}

	private TradePointOperationResult walletHold() {
		return new TradePointOperationResult(
			WalletType.TRADE,
			TradePointOperationType.HOLD,
			12_000L,
			200_000L,
			82_000L,
			118_000L,
			1001L
		);
	}

	private WalletQueryResult walletQuery() {
		return new WalletQueryResult(
			new WalletSummary(200_000L, 82_000L, 118_000L),
			new WalletSummary(2_000L, 0L, 2_000L)
		);
	}

	private void assertRejected(
		org.assertj.core.api.ThrowableAssert.ThrowingCallable callable,
		com.ssafy.bidlive.bid.domain.BidFailureCode expected
	) {
		assertThatThrownBy(callable)
			.isInstanceOfSatisfying(
				BidRejectedException.class,
				exception -> assertThat(exception.getFailureCode())
					.isEqualTo(expected)
			);
	}
}
