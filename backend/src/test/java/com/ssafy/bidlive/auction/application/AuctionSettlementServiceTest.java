package com.ssafy.bidlive.auction.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import com.ssafy.bidlive.auction.domain.VickreyPriceDecisionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** BID-07 Redis 선점·DB 정산·Redis 완료·결과 알림의 전체 호출 순서를 검증한다. */
@ExtendWith(MockitoExtension.class)
class AuctionSettlementServiceTest {

	private static final long AUCTION_ID = 42L;
	private static final Instant NOW =
		Instant.parse("2026-07-20T12:20:00Z");

	@Mock
	private AuctionSettlementClaimProcessor claimProcessor;
	@Mock
	private AuctionSettlementCompletionProcessor completionProcessor;
	@Mock
	private AuctionSettlementTransactionService transactionService;
	@Mock
	private AuctionSettlementNotificationPublisher notificationPublisher;

	private AuctionSettlementService service;

	@BeforeEach
	void setUp() {
		service = new AuctionSettlementService(
			claimProcessor,
			completionProcessor,
			transactionService,
			notificationPublisher,
			Clock.fixed(NOW, ZoneOffset.UTC)
		);
	}

	@Test
	void claimsSettlesCompletesRedisAndPublishesInOrder() {
		when(claimProcessor.claim(new AuctionSettlementClaimCommand(
			AUCTION_ID, NOW
		))).thenReturn(AuctionSettlementClaimResult.CLAIMED);
		AuctionSettlementResult settled = sold(true);
		when(transactionService.settle(AUCTION_ID, NOW)).thenReturn(settled);
		when(completionProcessor.complete(
			new AuctionSettlementCompletionCommand(AUCTION_ID, NOW)
		)).thenReturn(AuctionSettlementCompletionResult.COMPLETED);

		assertThat(service.settle(AUCTION_ID)).isEqualTo(settled);

		InOrder order = inOrder(
			claimProcessor,
			notificationPublisher,
			transactionService,
			completionProcessor
		);
		order.verify(claimProcessor).claim(
			new AuctionSettlementClaimCommand(AUCTION_ID, NOW)
		);
		order.verify(notificationPublisher).publishEnded(AUCTION_ID, NOW);
		order.verify(transactionService).settle(AUCTION_ID, NOW);
		order.verify(completionProcessor).complete(
			new AuctionSettlementCompletionCommand(AUCTION_ID, NOW)
		);
		order.verify(notificationPublisher).publishSettled(settled);
	}

	@Test
	void retriesAlreadySettlingAndPublishesRecoveredCommittedResult() {
		when(claimProcessor.claim(new AuctionSettlementClaimCommand(
			AUCTION_ID, NOW
		))).thenReturn(AuctionSettlementClaimResult.ALREADY_SETTLING);
		AuctionSettlementResult stored = sold(false);
		when(transactionService.settle(AUCTION_ID, NOW)).thenReturn(stored);
		when(completionProcessor.complete(
			new AuctionSettlementCompletionCommand(AUCTION_ID, NOW)
		)).thenReturn(AuctionSettlementCompletionResult.COMPLETED);

		assertThat(service.settle(AUCTION_ID)).isEqualTo(stored);

		verify(notificationPublisher, never()).publishEnded(AUCTION_ID, NOW);
		verify(notificationPublisher).publishSettled(stored);
	}

	@Test
	void settlesDbWhenRedisAlreadyEndedButDbWasStillLive() {
		when(claimProcessor.claim(new AuctionSettlementClaimCommand(
			AUCTION_ID, NOW
		))).thenReturn(AuctionSettlementClaimResult.ALREADY_ENDED);
		AuctionSettlementResult repaired = sold(true);
		when(transactionService.settle(AUCTION_ID, NOW)).thenReturn(repaired);
		when(completionProcessor.complete(
			new AuctionSettlementCompletionCommand(AUCTION_ID, NOW)
		)).thenReturn(AuctionSettlementCompletionResult.ALREADY_ENDED);

		assertThat(service.settle(AUCTION_ID)).isEqualTo(repaired);

		verify(transactionService).settle(AUCTION_ID, NOW);
		verify(notificationPublisher).publishSettled(repaired);
	}

	@Test
	void skipsBeforeDbWhenRedisDeadlineHasNotArrived() {
		when(claimProcessor.claim(new AuctionSettlementClaimCommand(
			AUCTION_ID, NOW
		))).thenReturn(AuctionSettlementClaimResult.AUCTION_NOT_DUE);

		AuctionSettlementResult result = service.settle(AUCTION_ID);

		assertThat(result.status())
			.isEqualTo(AuctionSettlementStatus.SKIPPED_NOT_DUE);
		verifyNoInteractions(
			transactionService,
			completionProcessor,
			notificationPublisher
		);
	}

	@Test
	void doesNotPublishSuccessWhenRedisCompletionFails() {
		when(claimProcessor.claim(new AuctionSettlementClaimCommand(
			AUCTION_ID, NOW
		))).thenReturn(AuctionSettlementClaimResult.CLAIMED);
		AuctionSettlementResult settled = sold(true);
		when(transactionService.settle(AUCTION_ID, NOW)).thenReturn(settled);
		when(completionProcessor.complete(
			new AuctionSettlementCompletionCommand(AUCTION_ID, NOW)
		)).thenReturn(AuctionSettlementCompletionResult.AUCTION_NOT_FOUND);

		assertThatThrownBy(() -> service.settle(AUCTION_ID))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("Redis settlement completion failed");
		verify(notificationPublisher, never()).publishSettled(settled);
	}

	private AuctionSettlementResult sold(boolean newlySettled) {
		return new AuctionSettlementResult(
			AUCTION_ID,
			AuctionSettlementStatus.SETTLED,
			newlySettled,
			100L,
			"낙찰*",
			82_000L,
			74_000L,
			50_000L,
			74_000L,
			VickreyPriceDecisionType.SECOND_PRICE,
			NOW
		);
	}
}
