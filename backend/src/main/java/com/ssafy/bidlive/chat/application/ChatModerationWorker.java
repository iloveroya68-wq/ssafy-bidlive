package com.ssafy.bidlive.chat.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import com.ssafy.bidlive.common.observability.RealtimeObservationMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/** 검열 작업 하나를 임대해 LLM 판정과 retry·DLQ 상태 전이를 조율합니다. */
public class ChatModerationWorker {
	private static final Logger log = LoggerFactory.getLogger(
		ChatModerationWorker.class
	);

	private final ChatModerationJobQueue jobQueue;
	private final ChatLlmModerationGateway gateway;
	private final ChatModerationOutcomeHandler outcomeHandler;
	private final RealtimeObservationMetrics metrics;
	private final Clock clock;
	private final int maxAttempts;
	private final Duration baseBackoff;
	private final Duration lease;

	public ChatModerationWorker(
		ChatModerationJobQueue jobQueue,
		ChatLlmModerationGateway gateway,
		ChatModerationOutcomeHandler outcomeHandler,
		RealtimeObservationMetrics metrics,
		Clock clock,
		int maxAttempts,
		Duration baseBackoff,
		Duration lease
	) {
		this.jobQueue = Objects.requireNonNull(jobQueue, "jobQueue");
		this.gateway = Objects.requireNonNull(gateway, "gateway");
		this.outcomeHandler = Objects.requireNonNull(
			outcomeHandler,
			"outcomeHandler"
		);
		this.metrics = Objects.requireNonNull(metrics, "metrics");
		this.clock = Objects.requireNonNull(clock, "clock");
		if (maxAttempts <= 0) {
			throw new IllegalArgumentException("maxAttempts must be positive");
		}
		this.maxAttempts = maxAttempts;
		this.baseBackoff = requirePositive(baseBackoff, "baseBackoff");
		this.lease = requirePositive(lease, "lease");
	}

	public boolean processNext() {
		Instant now = clock.instant();
		return jobQueue.claimDue(now, lease, maxAttempts)
			.map(this::processClaimed)
			.orElse(false);
	}

	private boolean processClaimed(ClaimedChatModerationJob claimed) {
		try (MDC.MDCCloseable ignored = MDC.putCloseable(
			"correlationId",
			claimed.job().messageId().value()
		)) {
			ChatModerationJudgement judgement = gateway.moderate(claimed.job());
			outcomeHandler.apply(claimed.job(), judgement);
			jobQueue.succeed(claimed.jobId(), clock.instant());
		} catch (ChatLlmModerationException exception) {
			handleFailure(claimed, exception);
		} catch (RuntimeException exception) {
			handleFailure(claimed, new ChatLlmModerationException(
				ChatLlmFailureCode.LLM_TRANSPORT_ERROR,
				true,
				exception
			));
		}
		return true;
	}

	private void handleFailure(
		ClaimedChatModerationJob claimed,
		ChatLlmModerationException failure
	) {
		Instant now = clock.instant();
		if (failure.retryable() && claimed.attemptCount() < maxAttempts) {
			jobQueue.retry(
				claimed.jobId(),
				now.plus(backoff(claimed)),
				failure.failureCode(),
				now
			);
			metrics.recordLlmRetry();
			log.atWarn()
				.addKeyValue("eventType", "LLM_MODERATION_RETRY")
				.addKeyValue("attempt", claimed.attemptCount())
				.addKeyValue("failureCode", failure.failureCode().name())
				.log("Chat moderation retry scheduled");
			return;
		}
		ChatLlmFailureCode code = failure.retryable()
			? ChatLlmFailureCode.LLM_RETRY_EXHAUSTED
			: failure.failureCode();
		jobQueue.deadLetter(claimed.jobId(), code, now);
		metrics.recordLlmDeadLetter();
		log.atError()
			.addKeyValue("eventType", "LLM_MODERATION_DLQ")
			.addKeyValue("attempt", claimed.attemptCount())
			.addKeyValue("failureCode", code.name())
			.log("Chat moderation moved to dead letter queue");
	}

	private Duration backoff(ClaimedChatModerationJob claimed) {
		long multiplier = 1L << Math.min(claimed.attemptCount() - 1, 20);
		long baseMillis = Math.multiplyExact(
			baseBackoff.toMillis(),
			multiplier
		);
		long jitterBound = Math.max(1L, baseBackoff.toMillis() / 2L);
		long jitter = Math.floorMod(claimed.jobId() * 31L, jitterBound);
		return Duration.ofMillis(Math.addExact(baseMillis, jitter));
	}

	private static Duration requirePositive(Duration value, String name) {
		if (value == null || value.isZero() || value.isNegative()) {
			throw new IllegalArgumentException(name + " must be positive");
		}
		return value;
	}
}
