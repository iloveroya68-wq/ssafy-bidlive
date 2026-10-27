package com.ssafy.bidlive.auction.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import com.ssafy.bidlive.auction.application.AuctionSettlementPersistence.BidCounts;
import com.ssafy.bidlive.auction.application.AuctionSettlementPersistence.FinalBid;
import com.ssafy.bidlive.auction.application.AuctionSettlementPersistence.SettlementTarget;
import com.ssafy.bidlive.auction.application.AuctionSettlementPersistence.StoredResult;
import com.ssafy.bidlive.deal.application.AuctionDealCreationService;
import com.ssafy.bidlive.prediction.application.PredictionSettlementCommand;
import com.ssafy.bidlive.prediction.application.PredictionSettlementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** BID-07 PostgreSQL 트랜잭션 안의 계산·포인트·Deal·예측 조립을 검증한다. */
@ExtendWith(MockitoExtension.class)
class AuctionSettlementTransactionServiceTest {

	private static final long AUCTION_ID = 42L;
	private static final Instant END_AT =
		Instant.parse("2026-07-20T12:20:00Z");

	@Mock
	private AuctionSettlementPersistence persistence;
	@Mock
	private AuctionSettlementPointService pointService;
	@Mock
	private AuctionDealCreationService dealCreationService;
	@Mock
	private PredictionSettlementService predictionSettlementService;

	private AuctionSettlementTransactionService service;

	@BeforeEach
	void setUp() {
		service = new AuctionSettlementTransactionService(
			persistence,
			pointService,
			dealCreationService,
			predictionSettlementService
		);
	}

	@Test
	void settlesSoldAuctionAndCreatesDeal() {
		givenLiveTarget(false);
		when(persistence.findFinalBids(AUCTION_ID)).thenReturn(List.of(
			new FinalBid(
				11L, 21L, 100L, "낙찰자", 82_000L,
				END_AT.minusSeconds(2)
			),
			new FinalBid(
				12L, 22L, 200L, "패찰자", 74_000L,
				END_AT.minusSeconds(1)
			)
		));
		when(persistence.countBids(AUCTION_ID))
			.thenReturn(new BidCounts(2, 3));
		when(persistence.createResultIfAbsent(any()))
			.thenReturn(OptionalLong.of(900L));
		when(persistence.markEnded(AUCTION_ID, END_AT)).thenReturn(1);
		when(dealCreationService.create(900L)).thenReturn(800L);

		AuctionSettlementResult result = service.settle(AUCTION_ID, END_AT);

		assertThat(result.status()).isEqualTo(AuctionSettlementStatus.SETTLED);
		assertThat(result.finalPrice()).isEqualTo(74_000L);
		assertThat(result.winner()).isEqualTo("낙**");
		verify(pointService).settle(
			org.mockito.ArgumentMatchers.eq(AUCTION_ID),
			any()
		);
		verify(dealCreationService).create(900L);
		ArgumentCaptor<PredictionSettlementCommand> prediction =
			ArgumentCaptor.forClass(PredictionSettlementCommand.class);
		verify(predictionSettlementService).settle(prediction.capture());
		assertThat(prediction.getValue().completionType())
			.isEqualTo(PredictionSettlementCommand.CompletionType.SOLD);
		assertThat(prediction.getValue().finalPrice()).isEqualTo(74_000L);
	}

	@Test
	void storesNoBidResultWithoutCreatingDeal() {
		givenLiveTarget(false);
		when(persistence.findFinalBids(AUCTION_ID)).thenReturn(List.of());
		when(persistence.countBids(AUCTION_ID))
			.thenReturn(new BidCounts(0, 0));
		when(persistence.createResultIfAbsent(any()))
			.thenReturn(OptionalLong.of(900L));
		when(persistence.markEnded(AUCTION_ID, END_AT)).thenReturn(1);

		AuctionSettlementResult result = service.settle(AUCTION_ID, END_AT);

		assertThat(result.status()).isEqualTo(AuctionSettlementStatus.NO_SALE);
		verify(dealCreationService, never()).create(any(Long.class));
		ArgumentCaptor<PredictionSettlementCommand> prediction =
			ArgumentCaptor.forClass(PredictionSettlementCommand.class);
		verify(predictionSettlementService).settle(prediction.capture());
		assertThat(prediction.getValue().completionType())
			.isEqualTo(PredictionSettlementCommand.CompletionType.NO_BID);
	}

	@Test
	void reusesStoredSoldResultWithoutChargingAgain() {
		givenLiveTarget(true);
		when(persistence.findStoredResult(AUCTION_ID)).thenReturn(Optional.of(
			new StoredResult(
				900L,
				"SOLD",
				"NORMAL",
				100L,
				"낙찰자",
				82_000L,
				74_000L,
				74_000L,
				END_AT
			)
		));
		when(persistence.markEnded(AUCTION_ID, END_AT)).thenReturn(1);

		AuctionSettlementResult result = service.settle(AUCTION_ID, END_AT);

		assertThat(result.status()).isEqualTo(AuctionSettlementStatus.SETTLED);
		assertThat(result.newlySettled()).isFalse();
		verifyNoInteractions(
			pointService,
			dealCreationService,
			predictionSettlementService
		);
	}

	@Test
	void skipsBeforeReadingBidsWhenPostgresDeadlineHasNotArrived() {
		when(persistence.lockTarget(AUCTION_ID)).thenReturn(Optional.of(
			new SettlementTarget(
				AUCTION_ID,
				"LIVE",
				50_000L,
				END_AT.plusSeconds(1),
				false
			)
		));

		AuctionSettlementResult result = service.settle(AUCTION_ID, END_AT);

		assertThat(result.status())
			.isEqualTo(AuctionSettlementStatus.SKIPPED_NOT_DUE);
		verify(persistence, never()).findFinalBids(AUCTION_ID);
		verifyNoInteractions(
			pointService,
			dealCreationService,
			predictionSettlementService
		);
	}

	private void givenLiveTarget(boolean resultExists) {
		when(persistence.lockTarget(AUCTION_ID)).thenReturn(Optional.of(
			new SettlementTarget(
				AUCTION_ID,
				"LIVE",
				50_000L,
				END_AT,
				resultExists
			)
		));
	}
}
