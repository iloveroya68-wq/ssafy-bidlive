package com.ssafy.bidlive.auction.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import com.ssafy.bidlive.auction.application.AuctionSettlementPersistence.OutstandingBidHold;
import com.ssafy.bidlive.auction.domain.VickreyPriceDecisionType;
import com.ssafy.bidlive.auction.domain.VickreySettlementDecision;
import com.ssafy.bidlive.wallet.application.TradePointOperationService;
import com.ssafy.bidlive.wallet.domain.PointLedgerReferenceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** BID-07 낙찰가 차감과 승자 차액·패자 홀드 해제를 검증한다. */
@ExtendWith(MockitoExtension.class)
class AuctionSettlementPointServiceTest {

	private static final long AUCTION_ID = 42L;
	private static final long WINNER_ID = 100L;
	private static final long LOSER_ID = 200L;

	@Mock
	private AuctionSettlementPersistence persistence;
	@Mock
	private TradePointOperationService tradePointOperationService;

	private AuctionSettlementPointService service;

	@BeforeEach
	void setUp() {
		service = new AuctionSettlementPointService(
			persistence,
			tradePointOperationService
		);
	}

	@Test
	void settlesCumulativeWinnerHoldsAndReleasesAllRemainders() {
		when(persistence.findOutstandingBidHolds(AUCTION_ID)).thenReturn(
			List.of(
				new OutstandingBidHold(13L, WINNER_ID, 22_000L),
				new OutstandingBidHold(12L, LOSER_ID, 74_000L),
				new OutstandingBidHold(11L, WINNER_ID, 60_000L)
			)
		);

		AuctionSettlementPointResult result = service.settle(
			AUCTION_ID,
			secondPriceDecision()
		);

		assertThat(result).isEqualTo(new AuctionSettlementPointResult(
			AUCTION_ID,
			74_000L,
			82_000L,
			2,
			2
		));
		InOrder order = inOrder(tradePointOperationService);
		order.verify(tradePointOperationService).settle(
			PointLedgerReferenceType.BID,
			11L,
			WINNER_ID,
			60_000L,
			"bid-07:42:bid:11:settle"
		);
		order.verify(tradePointOperationService).settle(
			PointLedgerReferenceType.BID,
			13L,
			WINNER_ID,
			14_000L,
			"bid-07:42:bid:13:settle"
		);
		order.verify(tradePointOperationService).release(
			PointLedgerReferenceType.BID,
			13L,
			WINNER_ID,
			8_000L,
			"bid-07:42:bid:13:release"
		);
		order.verify(tradePointOperationService).release(
			PointLedgerReferenceType.BID,
			12L,
			LOSER_ID,
			74_000L,
			"bid-07:42:bid:12:release"
		);
	}

	@Test
	void chargesReservePriceAndReleasesSoleBidderDifference() {
		when(persistence.findOutstandingBidHolds(AUCTION_ID)).thenReturn(
			List.of(new OutstandingBidHold(11L, WINNER_ID, 60_000L))
		);
		VickreySettlementDecision decision =
			VickreySettlementDecision.sold(
				WINNER_ID,
				60_000L,
				null,
				50_000L,
				VickreyPriceDecisionType.RESERVE_PRICE
			);

		AuctionSettlementPointResult result = service.settle(
			AUCTION_ID,
			decision
		);

		assertThat(result.settledAmount()).isEqualTo(50_000L);
		assertThat(result.releasedAmount()).isEqualTo(10_000L);
		verify(tradePointOperationService).settle(
			PointLedgerReferenceType.BID,
			11L,
			WINNER_ID,
			50_000L,
			"bid-07:42:bid:11:settle"
		);
		verify(tradePointOperationService).release(
			PointLedgerReferenceType.BID,
			11L,
			WINNER_ID,
			10_000L,
			"bid-07:42:bid:11:release"
		);
	}

	@Test
	void leavesPointsUntouchedForNoBidNoSale() {
		when(persistence.findOutstandingBidHolds(AUCTION_ID))
			.thenReturn(List.of());

		AuctionSettlementPointResult result = service.settle(
			AUCTION_ID,
			VickreySettlementDecision.noSale()
		);

		assertThat(result).isEqualTo(new AuctionSettlementPointResult(
			AUCTION_ID, 0L, 0L, 0, 0
		));
		verifyNoInteractions(tradePointOperationService);
	}

	@Test
	void rejectsWinnerHoldMismatchBeforeChangingAnyWallet() {
		when(persistence.findOutstandingBidHolds(AUCTION_ID)).thenReturn(
			List.of(
				new OutstandingBidHold(11L, WINNER_ID, 60_000L),
				new OutstandingBidHold(12L, LOSER_ID, 74_000L)
			)
		);

		assertThatThrownBy(() -> service.settle(
			AUCTION_ID,
			secondPriceDecision()
		))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("winner hold must equal the highest bid amount");
		verifyNoInteractions(tradePointOperationService);
	}

	@Test
	void rejectsNoSaleWithStaleHoldBeforeChangingAnyWallet() {
		when(persistence.findOutstandingBidHolds(AUCTION_ID)).thenReturn(
			List.of(new OutstandingBidHold(11L, WINNER_ID, 60_000L))
		);

		assertThatThrownBy(() -> service.settle(
			AUCTION_ID,
			VickreySettlementDecision.noSale()
		))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage(
				"no-sale auction must not have outstanding bid holds"
			);
		verifyNoInteractions(tradePointOperationService);
	}

	private VickreySettlementDecision secondPriceDecision() {
		return VickreySettlementDecision.sold(
			WINNER_ID,
			82_000L,
			74_000L,
			74_000L,
			VickreyPriceDecisionType.SECOND_PRICE
		);
	}
}
