package com.ssafy.bidlive.auction.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import com.ssafy.bidlive.auction.application.AuctionForceEndPersistence.ForceEndTarget;
import com.ssafy.bidlive.auction.application.AuctionForceEndPersistence.OutstandingBidHold;
import com.ssafy.bidlive.auction.application.AuctionForceEndPersistence.ResultCounts;
import com.ssafy.bidlive.auction.domain.AuctionCanceledEvent;
import com.ssafy.bidlive.auction.domain.AuctionForceEndErrorCode;
import com.ssafy.bidlive.auction.domain.AuctionSellerEndErrorCode;
import com.ssafy.bidlive.auction.domain.AuctionTerminationReason;
import com.ssafy.bidlive.prediction.application.PredictionSettlementCommand;
import com.ssafy.bidlive.prediction.application.PredictionSettlementResult;
import com.ssafy.bidlive.prediction.application.PredictionSettlementService;
import com.ssafy.bidlive.wallet.application.TradePointOperationService;
import com.ssafy.bidlive.wallet.domain.PointLedgerReferenceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/**
 * DEAL-09 강제 종료의 상태 판정·환급 위임·멱등성을 검증한다.
 *
 * <p>여기서 깨지면 조용히 틀리는 것들이다: 상태 가드가 새면 끝난 경매를 또 종료해 **중복 환급**이
 * 나가고, 이벤트를 상태 반영 전에 쏘면 롤백된 종료를 화면이 확정으로 그린다.
 */
@ExtendWith(MockitoExtension.class)
class AuctionForceEndServiceTest {

	private static final long AUCTION_ID = 42L;
	private static final long SELLER_ID = 7L;
	private static final long RESULT_ID = 900L;
	private static final String REASON = "POLICY_VIOLATION";
	private static final Instant NOW = Instant.parse("2026-07-31T06:00:00Z");

	@Mock
	private AuctionForceEndPersistence persistence;
	@Mock
	private TradePointOperationService tradePointOperationService;
	@Mock
	private PredictionSettlementService predictionSettlementService;
	@Mock
	private ApplicationEventPublisher eventPublisher;

	private AuctionForceEndService service;

	@BeforeEach
	void setUp() {
		service = new AuctionForceEndService(
			persistence,
			tradePointOperationService,
			predictionSettlementService,
			eventPublisher,
			Clock.fixed(NOW, ZoneOffset.UTC)
		);
	}

	@Test
	void endsLiveAuctionAndReleasesHoldsThenRefundsPredictions() {
		givenTarget("LIVE");
		when(persistence.markEnded(AUCTION_ID, NOW)).thenReturn(1);
		when(persistence.countBids(AUCTION_ID)).thenReturn(new ResultCounts(3, 5));
		when(persistence.createResultIfAbsent(AUCTION_ID, new ResultCounts(3, 5), AuctionTerminationReason.FORCED))
			.thenReturn(RESULT_ID);
		when(persistence.findOutstandingBidHolds(AUCTION_ID)).thenReturn(List.of(
			new OutstandingBidHold(11L, 100L, 5_000L),
			new OutstandingBidHold(12L, 200L, 3_000L)
		));
		when(predictionSettlementService.settle(any()))
			.thenReturn(new PredictionSettlementResult(
				AUCTION_ID, 4, 4, 0, 4_000L, 4_000L
			));

		AuctionForceEndResult result = service.forceEnd(AUCTION_ID, REASON);

		assertThat(result.endedAt()).isEqualTo(NOW);
		assertThat(result.releasedHoldCount()).isEqualTo(2);
		assertThat(result.releasedHoldAmount()).isEqualTo(8_000L);
		assertThat(result.refundedPredictionCount()).isEqualTo(4);

		verify(tradePointOperationService).release(
			PointLedgerReferenceType.BID, 11L, 100L, 5_000L, "force-end:bid:11"
		);
		verify(tradePointOperationService).release(
			PointLedgerReferenceType.BID, 12L, 200L, 3_000L, "force-end:bid:12"
		);
	}

	@Test
	void refundsPredictionsWithoutFinalPriceUnderForcedCompletion() {
		givenEndableAuctionWithNoHolds("WAITING");

		service.forceEnd(AUCTION_ID, REASON);

		ArgumentCaptor<PredictionSettlementCommand> captor =
			ArgumentCaptor.forClass(PredictionSettlementCommand.class);
		verify(predictionSettlementService).settle(captor.capture());
		PredictionSettlementCommand command = captor.getValue();
		// finalPrice가 있으면 배당 정산으로 갈린다 — 강제 종료는 전액 환불이라 null이어야 한다.
		assertThat(command.finalPrice()).isNull();
		assertThat(command.refunded()).isTrue();
		assertThat(command.completionType())
			.isEqualTo(PredictionSettlementCommand.CompletionType.FORCED);
		assertThat(command.auctionResultId()).isEqualTo(RESULT_ID);
	}

	@Test
	void publishesCanceledEventWithReasonAndEndedAt() {
		givenEndableAuctionWithNoHolds("LIVE");

		service.forceEnd(AUCTION_ID, REASON);

		ArgumentCaptor<AuctionCanceledEvent> captor =
			ArgumentCaptor.forClass(AuctionCanceledEvent.class);
		verify(eventPublisher).publishEvent(captor.capture());
		assertThat(captor.getValue().auctionId()).isEqualTo(AUCTION_ID);
		assertThat(captor.getValue().reason()).isEqualTo(REASON);
		assertThat(captor.getValue().endedAt()).isEqualTo(NOW);
	}

	@Test
	void rejectsAlreadyEndedAuctionWithoutTouchingPoints() {
		givenTarget("ENDED");

		assertThatThrownBy(() -> service.forceEnd(AUCTION_ID, REASON))
			.isInstanceOf(AuctionForceEndException.class)
			.extracting(e -> ((AuctionForceEndException) e).getErrorCode())
			.isEqualTo(AuctionForceEndErrorCode.AUCTION_NOT_ENDABLE);

		// 재실행이 중복 환급을 내지 않는다는 근거가 이 줄이다.
		verify(persistence, never()).markEnded(anyLong(), any());
		verifyNoInteractions(tradePointOperationService, predictionSettlementService);
	}

	@Test
	void rejectsDeletedAuction() {
		when(persistence.findTarget(AUCTION_ID))
			.thenReturn(Optional.of(new ForceEndTarget(AUCTION_ID, SELLER_ID, "WAITING", true)));

		assertThatThrownBy(() -> service.forceEnd(AUCTION_ID, REASON))
			.isInstanceOf(AuctionForceEndException.class)
			.extracting(e -> ((AuctionForceEndException) e).getErrorCode())
			.isEqualTo(AuctionForceEndErrorCode.AUCTION_NOT_ENDABLE);
	}

	@Test
	void rejectsWhenAnotherRequestEndedItFirst() {
		// 판정과 전이 사이의 경쟁. 조건부 UPDATE가 0행을 돌려주는 그 순간이다.
		givenTarget("LIVE");
		when(persistence.markEnded(AUCTION_ID, NOW)).thenReturn(0);

		assertThatThrownBy(() -> service.forceEnd(AUCTION_ID, REASON))
			.isInstanceOf(AuctionForceEndException.class)
			.extracting(e -> ((AuctionForceEndException) e).getErrorCode())
			.isEqualTo(AuctionForceEndErrorCode.AUCTION_NOT_ENDABLE);

		verifyNoInteractions(tradePointOperationService, predictionSettlementService);
		verify(eventPublisher, never()).publishEvent(any(AuctionCanceledEvent.class));
	}

	@Test
	void rejectsMissingAuction() {
		when(persistence.findTarget(AUCTION_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.forceEnd(AUCTION_ID, REASON))
			.isInstanceOf(AuctionForceEndException.class)
			.extracting(e -> ((AuctionForceEndException) e).getErrorCode())
			.isEqualTo(AuctionForceEndErrorCode.AUCTION_NOT_FOUND);
	}

	@Test
	void rejectsBlankReason() {
		assertThatThrownBy(() -> service.forceEnd(AUCTION_ID, " "))
			.isInstanceOf(AuctionForceEndException.class)
			.extracting(e -> ((AuctionForceEndException) e).getErrorCode())
			.isEqualTo(AuctionForceEndErrorCode.INVALID_REASON);

		verifyNoInteractions(persistence);
	}

	private void givenTarget(String status) {
		when(persistence.findTarget(AUCTION_ID))
			.thenReturn(Optional.of(new ForceEndTarget(AUCTION_ID, SELLER_ID, status, false)));
	}

	private void givenEndableAuctionWithNoHolds(String status) {
		givenTarget(status);
		when(persistence.markEnded(AUCTION_ID, NOW)).thenReturn(1);
		when(persistence.countBids(AUCTION_ID)).thenReturn(new ResultCounts(0, 0));
		when(persistence.createResultIfAbsent(anyLong(), any(), any())).thenReturn(RESULT_ID);
		when(persistence.findOutstandingBidHolds(AUCTION_ID)).thenReturn(List.of());
		when(predictionSettlementService.settle(any()))
			.thenReturn(new PredictionSettlementResult(
				AUCTION_ID, 0, 0, 0, 0L, 0L
			));
	}

	// ── ROOM-09 판매자 종료 (S15P11A704-687) ──
	//
	// 운영 종료와 마감 절차는 같고 대상 판정만 다르다. 여기서 깨지면 조용히 틀리는 것들:
	// 소유 판정이 새면 **남이 내 방을 끝낼 수 있고**, 사유가 FORCED로 나가면 참여자 화면이
	// 「운영팀이 종료했어요」로 거짓말을 한다.

	@Test
	void sellerEndRejectsAnonymousCaller() {
		assertThatThrownBy(() -> service.endBySeller(AUCTION_ID, null))
			.isInstanceOf(AuctionSellerEndException.class)
			.extracting(e -> ((AuctionSellerEndException) e).getErrorCode())
			.isEqualTo(AuctionSellerEndErrorCode.UNAUTHORIZED);
		verifyNoInteractions(persistence);
	}

	@Test
	void sellerEndRejectsNonSeller() {
		givenTarget("LIVE");

		assertThatThrownBy(() -> service.endBySeller(AUCTION_ID, SELLER_ID + 1))
			.isInstanceOf(AuctionSellerEndException.class)
			.extracting(e -> ((AuctionSellerEndException) e).getErrorCode())
			.isEqualTo(AuctionSellerEndErrorCode.NOT_AUCTION_SELLER);
		verify(persistence, never()).markEnded(anyLong(), any());
	}

	/** 운영 종료는 삭제된 방을 409로 알려 주지만, 이쪽은 아무나 부를 수 있어 존재를 감춘다. */
	@Test
	void sellerEndHidesDeletedAuctionAsNotFound() {
		when(persistence.findTarget(AUCTION_ID))
			.thenReturn(Optional.of(new ForceEndTarget(AUCTION_ID, SELLER_ID, "LIVE", true)));

		assertThatThrownBy(() -> service.endBySeller(AUCTION_ID, SELLER_ID))
			.isInstanceOf(AuctionSellerEndException.class)
			.extracting(e -> ((AuctionSellerEndException) e).getErrorCode())
			.isEqualTo(AuctionSellerEndErrorCode.AUCTION_NOT_FOUND);
	}

	@Test
	void sellerEndRejectsAlreadyEndedAuction() {
		givenTarget("ENDED");

		assertThatThrownBy(() -> service.endBySeller(AUCTION_ID, SELLER_ID))
			.isInstanceOf(AuctionSellerEndException.class)
			.extracting(e -> ((AuctionSellerEndException) e).getErrorCode())
			.isEqualTo(AuctionSellerEndErrorCode.AUCTION_NOT_ENDABLE);
		verify(persistence, never()).markEnded(anyLong(), any());
	}

	/** 전이가 0행이면 그 사이 다른 요청이 먼저 끝낸 것이다 — 환급 단계에 도달하면 중복 환급이 된다. */
	@Test
	void sellerEndStopsWhenTransitionLostRace() {
		givenTarget("LIVE");
		when(persistence.markEnded(AUCTION_ID, NOW)).thenReturn(0);

		assertThatThrownBy(() -> service.endBySeller(AUCTION_ID, SELLER_ID))
			.isInstanceOf(AuctionSellerEndException.class)
			.extracting(e -> ((AuctionSellerEndException) e).getErrorCode())
			.isEqualTo(AuctionSellerEndErrorCode.AUCTION_NOT_ENDABLE);
		verifyNoInteractions(predictionSettlementService);
	}

	@Test
	void sellerEndRecordsHostLeftAndPublishesThatReason() {
		givenEndableAuctionWithNoHolds("LIVE");

		service.endBySeller(AUCTION_ID, SELLER_ID);

		verify(persistence).createResultIfAbsent(
			AUCTION_ID,
			new ResultCounts(0, 0),
			AuctionTerminationReason.HOST_LEFT
		);
		ArgumentCaptor<AuctionCanceledEvent> captor =
			ArgumentCaptor.forClass(AuctionCanceledEvent.class);
		verify(eventPublisher).publishEvent(captor.capture());
		assertThat(captor.getValue().terminationReason())
			.isEqualTo(AuctionTerminationReason.HOST_LEFT);
	}

	/** 입찰이 있어도 낙찰로 넘어가지 않는다 — 홀드는 전액 해제고 결과는 유찰이다. */
	@Test
	void sellerEndReleasesHoldsWithoutAwardingWinner() {
		givenTarget("LIVE");
		when(persistence.markEnded(AUCTION_ID, NOW)).thenReturn(1);
		when(persistence.countBids(AUCTION_ID)).thenReturn(new ResultCounts(2, 3));
		when(persistence.createResultIfAbsent(anyLong(), any(), any())).thenReturn(RESULT_ID);
		when(persistence.findOutstandingBidHolds(AUCTION_ID)).thenReturn(List.of(
			new OutstandingBidHold(11L, 100L, 5_000L),
			new OutstandingBidHold(12L, 200L, 7_000L)
		));
		when(predictionSettlementService.settle(any()))
			.thenReturn(new PredictionSettlementResult(AUCTION_ID, 0, 0, 0, 0L, 0L));

		AuctionForceEndResult result = service.endBySeller(AUCTION_ID, SELLER_ID);

		assertThat(result.releasedHoldCount()).isEqualTo(2);
		assertThat(result.releasedHoldAmount()).isEqualTo(12_000L);
		verify(tradePointOperationService).release(
			PointLedgerReferenceType.BID, 11L, 100L, 5_000L, "force-end:bid:11"
		);
		verify(tradePointOperationService).release(
			PointLedgerReferenceType.BID, 12L, 200L, 7_000L, "force-end:bid:12"
		);
	}
}
