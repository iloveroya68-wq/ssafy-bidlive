package com.ssafy.bidlive.auction.infrastructure;

import static com.ssafy.bidlive.auction.application.AuctionSettlementStatus.SKIPPED_NOT_DUE;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

import com.ssafy.bidlive.auction.application.AuctionSettlementResult;
import com.ssafy.bidlive.auction.application.AuctionSettlementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 마감 예약의 실행 시각과 연장 교체·정산 실패 재시도를 검증한다. */
@ExtendWith(MockitoExtension.class)
class AuctionSettlementDeadlineSchedulerTest {

	private static final long AUCTION_ID = 91L;
	private static final Instant NOW = Instant.parse("2026-08-02T03:00:00Z");
	private static final Instant END_AT = NOW.plusSeconds(10);

	@Mock
	private ScheduledExecutorService executor;

	@Mock
	private AuctionSettlementService settlementService;

	@Mock
	private ScheduledFuture<?> firstFuture;

	@Mock
	private ScheduledFuture<?> secondFuture;

	private AuctionSettlementDeadlineScheduler scheduler;

	@BeforeEach
	void setUp() {
		scheduler = new AuctionSettlementDeadlineScheduler(
			executor,
			settlementService,
			Clock.fixed(NOW, ZoneOffset.UTC)
		);
	}

	@Test
	void schedulesSettlementAtTheRequestedDeadline() {
		doReturn(firstFuture).when(executor)
			.schedule(any(Runnable.class), eq(10_000L), eq(MILLISECONDS));

		assertThat(scheduler.schedule(AUCTION_ID, END_AT)).isTrue();

		ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
		verify(executor).schedule(task.capture(), eq(10_000L), eq(MILLISECONDS));
		task.getValue().run();
		verify(settlementService).settle(AUCTION_ID);
	}

	@Test
	void replacesThePreviousTaskOnlyWithALaterDeadline() {
		Instant extendedEndAt = END_AT.plusSeconds(30);
		doReturn(firstFuture).when(executor)
			.schedule(any(Runnable.class), eq(10_000L), eq(MILLISECONDS));
		doReturn(secondFuture).when(executor)
			.schedule(any(Runnable.class), eq(40_000L), eq(MILLISECONDS));

		assertThat(scheduler.schedule(AUCTION_ID, END_AT)).isTrue();
		assertThat(scheduler.schedule(AUCTION_ID, extendedEndAt)).isTrue();

		verify(firstFuture).cancel(false);
		ArgumentCaptor<Runnable> tasks = ArgumentCaptor.forClass(Runnable.class);
		verify(executor, times(2)).schedule(
			tasks.capture(),
			anyLong(),
			eq(MILLISECONDS)
		);
		tasks.getAllValues().getFirst().run();
		verifyNoInteractions(settlementService);

		tasks.getAllValues().getLast().run();
		verify(settlementService).settle(AUCTION_ID);
	}

	@Test
	void ignoresTheSameOrAnOlderDeadline() {
		doReturn(firstFuture).when(executor)
			.schedule(any(Runnable.class), eq(10_000L), eq(MILLISECONDS));

		assertThat(scheduler.schedule(AUCTION_ID, END_AT)).isTrue();
		assertThat(scheduler.schedule(AUCTION_ID, END_AT)).isFalse();
		assertThat(scheduler.schedule(AUCTION_ID, END_AT.minusSeconds(1)))
			.isFalse();

		verify(executor).schedule(any(Runnable.class), eq(10_000L), eq(MILLISECONDS));
		verify(firstFuture, never()).cancel(false);
	}

	@Test
	void clearsTheReservationWhenTheExecutorRejectsIt() {
		doThrow(new RejectedExecutionException("executor unavailable"))
			.doReturn(firstFuture)
			.when(executor)
			.schedule(any(Runnable.class), eq(10_000L), eq(MILLISECONDS));

		assertThatThrownBy(() -> scheduler.schedule(AUCTION_ID, END_AT))
			.isInstanceOf(RejectedExecutionException.class);
		assertThat(scheduler.schedule(AUCTION_ID, END_AT)).isTrue();

		verify(executor, times(2)).schedule(
			any(Runnable.class),
			eq(10_000L),
			eq(MILLISECONDS)
		);
	}

	@Test
	void doesNotLeaveAStaleReservationWhenAPastTaskRunsImmediately() {
		Instant pastEndAt = NOW.minusSeconds(1);
		when(executor.schedule(any(Runnable.class), eq(0L), eq(MILLISECONDS)))
			.thenAnswer(invocation -> {
				invocation.getArgument(0, Runnable.class).run();
				return firstFuture;
			})
			.thenAnswer(invocation -> {
				invocation.getArgument(0, Runnable.class).run();
				return secondFuture;
			});

		assertThat(scheduler.schedule(AUCTION_ID, pastEndAt)).isTrue();
		assertThat(scheduler.schedule(AUCTION_ID, pastEndAt)).isTrue();

		verify(settlementService, times(2)).settle(AUCTION_ID);
	}

	@Test
	void retriesSettlementFiveSecondsAfterAnExecutionFailure() {
		doReturn(firstFuture).when(executor)
			.schedule(any(Runnable.class), eq(10_000L), eq(MILLISECONDS));
		doReturn(secondFuture).when(executor)
			.schedule(any(Runnable.class), eq(5_000L), eq(MILLISECONDS));
		when(settlementService.settle(AUCTION_ID))
			.thenThrow(new IllegalStateException("database unavailable"))
			.thenReturn(null);

		assertThat(scheduler.schedule(AUCTION_ID, END_AT)).isTrue();
		ArgumentCaptor<Runnable> initialTask = ArgumentCaptor.forClass(
			Runnable.class
		);
		verify(executor).schedule(
			initialTask.capture(),
			eq(10_000L),
			eq(MILLISECONDS)
		);

		initialTask.getValue().run();
		ArgumentCaptor<Runnable> retryTask = ArgumentCaptor.forClass(
			Runnable.class
		);
		verify(executor).schedule(
			retryTask.capture(),
			eq(5_000L),
			eq(MILLISECONDS)
		);
		retryTask.getValue().run();

		verify(settlementService, times(2)).settle(AUCTION_ID);
	}

	@Test
	void retriesSettlementWhenTheDeadlineHasNotArrivedYet() {
		doReturn(firstFuture).when(executor)
			.schedule(any(Runnable.class), eq(10_000L), eq(MILLISECONDS));
		doReturn(secondFuture).when(executor)
			.schedule(any(Runnable.class), eq(5_000L), eq(MILLISECONDS));
		when(settlementService.settle(AUCTION_ID))
			.thenReturn(AuctionSettlementResult.skipped(AUCTION_ID, SKIPPED_NOT_DUE))
			.thenReturn(null);

		assertThat(scheduler.schedule(AUCTION_ID, END_AT)).isTrue();
		ArgumentCaptor<Runnable> initialTask = ArgumentCaptor.forClass(
			Runnable.class
		);
		verify(executor).schedule(
			initialTask.capture(),
			eq(10_000L),
			eq(MILLISECONDS)
		);

		initialTask.getValue().run();
		ArgumentCaptor<Runnable> retryTask = ArgumentCaptor.forClass(
			Runnable.class
		);
		verify(executor).schedule(
			retryTask.capture(),
			eq(5_000L),
			eq(MILLISECONDS)
		);
		retryTask.getValue().run();

		verify(settlementService, times(2)).settle(AUCTION_ID);
	}

	@Test
	void keepsALaterExtensionInsteadOfRegisteringAnOlderRetry() {
		Instant extendedEndAt = END_AT.plusSeconds(30);
		doReturn(firstFuture).when(executor)
			.schedule(any(Runnable.class), eq(10_000L), eq(MILLISECONDS));
		doReturn(secondFuture).when(executor)
			.schedule(any(Runnable.class), eq(40_000L), eq(MILLISECONDS));
		when(settlementService.settle(AUCTION_ID)).thenAnswer(invocation -> {
			scheduler.schedule(AUCTION_ID, extendedEndAt);
			throw new IllegalStateException("database unavailable");
		});

		assertThat(scheduler.schedule(AUCTION_ID, END_AT)).isTrue();
		ArgumentCaptor<Runnable> initialTask = ArgumentCaptor.forClass(
			Runnable.class
		);
		verify(executor).schedule(
			initialTask.capture(),
			eq(10_000L),
			eq(MILLISECONDS)
		);
		initialTask.getValue().run();

		verify(executor).schedule(
			any(Runnable.class),
			eq(40_000L),
			eq(MILLISECONDS)
		);
		verify(executor, never()).schedule(
			any(Runnable.class),
			eq(5_000L),
			eq(MILLISECONDS)
		);
	}

	@Test
	void rejectsInvalidReservationValues() {
		assertThatThrownBy(() -> scheduler.schedule(0L, END_AT))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("auctionId must be positive");
		assertThatThrownBy(() -> scheduler.schedule(AUCTION_ID, null))
			.isInstanceOf(NullPointerException.class)
			.hasMessage("endAt");

		verifyNoInteractions(executor, settlementService);
	}
}
