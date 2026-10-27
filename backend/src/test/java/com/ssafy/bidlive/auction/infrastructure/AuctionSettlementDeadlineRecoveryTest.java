package com.ssafy.bidlive.auction.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;

import com.ssafy.bidlive.auction.application.AuctionSettlementDeadlineReader;
import com.ssafy.bidlive.auction.application.AuctionSettlementDeadlineReader.Deadline;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

/** 서버 기동 시 DB의 LIVE 경매 마감 예약이 빠짐없이 복구되는지 검증한다. */
@ExtendWith(MockitoExtension.class)
class AuctionSettlementDeadlineRecoveryTest {

	private static final Instant FIRST_END_AT =
		Instant.parse("2026-08-02T09:00:00Z");
	private static final Instant SECOND_END_AT =
		Instant.parse("2026-08-02T09:10:00Z");

	@Mock
	private AuctionSettlementDeadlineReader deadlineReader;

	@Mock
	private AuctionSettlementDeadlineScheduler deadlineScheduler;

	@InjectMocks
	private AuctionSettlementDeadlineRecovery recovery;

	@Test
	void restoresEveryLiveAuctionDeadlineInDatabaseOrder() {
		when(deadlineReader.findLiveDeadlines()).thenReturn(List.of(
			new Deadline(101L, FIRST_END_AT),
			new Deadline(102L, SECOND_END_AT)
		));

		recovery.recoverLiveAuctionDeadlines();

		InOrder order = inOrder(deadlineScheduler);
		order.verify(deadlineScheduler).schedule(101L, FIRST_END_AT);
		order.verify(deadlineScheduler).schedule(102L, SECOND_END_AT);
	}

	@Test
	void exposesReservationFailureInsteadOfSilentlyLosingDeadlines() {
		when(deadlineReader.findLiveDeadlines()).thenReturn(List.of(
			new Deadline(101L, FIRST_END_AT),
			new Deadline(102L, SECOND_END_AT)
		));
		when(deadlineScheduler.schedule(101L, FIRST_END_AT))
			.thenThrow(new RejectedExecutionException("executor unavailable"));

		assertThatThrownBy(recovery::recoverLiveAuctionDeadlines)
			.isInstanceOf(RejectedExecutionException.class);
		verify(deadlineScheduler, never()).schedule(102L, SECOND_END_AT);
	}

	@Test
	void restoresDeadlinesWhenTheApplicationIsReady() throws Exception {
		Method listener = AuctionSettlementDeadlineRecovery.class.getMethod(
			"recoverLiveAuctionDeadlines"
		);
		EventListener annotation = listener.getAnnotation(EventListener.class);

		assertThat(annotation).isNotNull();
		assertThat(annotation.value()).containsExactly(ApplicationReadyEvent.class);
	}
}
