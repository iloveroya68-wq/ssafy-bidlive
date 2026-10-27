package com.ssafy.bidlive.auction.infrastructure;

import static com.ssafy.bidlive.auction.application.AuctionSettlementStatus.SKIPPED_NOT_DUE;
import static com.ssafy.bidlive.auction.infrastructure.AuctionSettlementDeadlineSchedulingConfiguration.DEADLINE_EXECUTOR_BEAN;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.ssafy.bidlive.auction.application.AuctionSettlementResult;
import com.ssafy.bidlive.auction.application.AuctionSettlementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** 경매별 최신 마감 시각에 비크리 정산을 실행하고 실패하면 지연 재시도한다. */
@Component
public class AuctionSettlementDeadlineScheduler {

	private static final long[] RETRY_DELAYS_MILLIS = {
		5_000L,
		10_000L,
		20_000L,
		40_000L,
		60_000L
	};
	private static final Logger log = LoggerFactory.getLogger(
		AuctionSettlementDeadlineScheduler.class
	);

	private final ScheduledExecutorService executor;
	private final AuctionSettlementService settlementService;
	private final Clock clock;
	private final ConcurrentMap<Long, ScheduledDeadline> deadlines =
		new ConcurrentHashMap<>();

	@Autowired
	public AuctionSettlementDeadlineScheduler(
		@Qualifier(DEADLINE_EXECUTOR_BEAN) ScheduledExecutorService executor,
		AuctionSettlementService settlementService
	) {
		this(executor, settlementService, Clock.systemUTC());
	}

	AuctionSettlementDeadlineScheduler(
		ScheduledExecutorService executor,
		AuctionSettlementService settlementService,
		Clock clock
	) {
		this.executor = Objects.requireNonNull(executor, "executor");
		this.settlementService = Objects.requireNonNull(
			settlementService,
			"settlementService"
		);
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	/** 현재 예약보다 늦은 마감 시각만 받아 기존 작업을 취소하고 교체한다. */
	public boolean schedule(long auctionId, Instant endAt) {
		if (auctionId <= 0) {
			throw new IllegalArgumentException("auctionId must be positive");
		}
		Objects.requireNonNull(endAt, "endAt");

		return schedule(
			auctionId,
			new ScheduledDeadline(endAt, 0),
			delayUntil(endAt)
		);
	}

	private boolean schedule(
		long auctionId,
		ScheduledDeadline replacement,
		long delayMillis
	) {
		AtomicBoolean accepted = new AtomicBoolean(false);
		deadlines.compute(auctionId, (id, current) -> {
			if (current != null
				&& !replacement.endAt().isAfter(current.endAt())) {
				return current;
			}
			if (current != null) {
				current.cancel();
			}
			accepted.set(true);
			return replacement;
		});
		if (!accepted.get()) {
			return false;
		}

		try {
			ScheduledFuture<?> future = executor.schedule(
				() -> execute(auctionId, replacement),
				delayMillis,
				TimeUnit.MILLISECONDS
			);
			replacement.attach(future);
			return true;
		} catch (RuntimeException exception) {
			deadlines.remove(auctionId, replacement);
			replacement.cancel();
			throw exception;
		}
	}

	private void execute(long auctionId, ScheduledDeadline deadline) {
		if (!deadlines.remove(auctionId, deadline)) {
			return;
		}
		try {
			AuctionSettlementResult result = settlementService.settle(auctionId);
			if (result != null && result.status() == SKIPPED_NOT_DUE) {
				log.debug(
					"Scheduled auction settlement was not due yet; retrying: auctionId={}, retryAttempt={}",
					auctionId,
					deadline.retryAttempt() + 1
				);
				scheduleRetry(auctionId, deadline, null);
			}
		} catch (RuntimeException exception) {
			log.warn(
				"Scheduled auction settlement failed; retrying: auctionId={}, retryAttempt={}, failureType={}",
				auctionId,
				deadline.retryAttempt() + 1,
				exception.getClass().getSimpleName(),
				exception
			);
			scheduleRetry(auctionId, deadline, exception);
		}
	}

	private void scheduleRetry(
		long auctionId,
		ScheduledDeadline deadline,
		RuntimeException failure
	) {
		try {
			schedule(
				auctionId,
				new ScheduledDeadline(
					deadline.endAt(),
					deadline.retryAttempt() + 1
				),
				retryDelayMillis(deadline.retryAttempt())
			);
		} catch (RuntimeException retryFailure) {
			if (failure != null) {
				retryFailure.addSuppressed(failure);
			}
			log.error(
				"Failed to register auction settlement retry: auctionId={}, failureType={}",
				auctionId,
				retryFailure.getClass().getSimpleName(),
				retryFailure
			);
			throw retryFailure;
		}
	}

	private long delayUntil(Instant endAt) {
		return Math.max(
			0L,
			Duration.between(clock.instant(), endAt).toMillis()
		);
	}

	private static long retryDelayMillis(int retryAttempt) {
		return RETRY_DELAYS_MILLIS[Math.min(
			retryAttempt,
			RETRY_DELAYS_MILLIS.length - 1
		)];
	}

	private static final class ScheduledDeadline {

		private final Instant endAt;
		private final int retryAttempt;
		private ScheduledFuture<?> future;
		private boolean cancelled;

		private ScheduledDeadline(Instant endAt, int retryAttempt) {
			this.endAt = endAt;
			this.retryAttempt = retryAttempt;
		}

		private Instant endAt() {
			return endAt;
		}

		private int retryAttempt() {
			return retryAttempt;
		}

		private synchronized void attach(ScheduledFuture<?> scheduledFuture) {
			future = Objects.requireNonNull(
				scheduledFuture,
				"scheduledFuture"
			);
			if (cancelled) {
				future.cancel(false);
			}
		}

		private synchronized void cancel() {
			cancelled = true;
			if (future != null) {
				future.cancel(false);
			}
		}
	}
}
