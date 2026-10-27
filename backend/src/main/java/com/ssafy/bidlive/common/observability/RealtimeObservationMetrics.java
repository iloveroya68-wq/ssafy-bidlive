package com.ssafy.bidlive.common.observability;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

/** 실시간 연결·채팅·Redis·LLM 상태를 저카디널리티 메트릭으로 기록합니다. */
@Component
public class RealtimeObservationMetrics {

	public static final String ACTIVE_STOMP_SESSIONS =
		"bidlive.stomp.sessions.active";
	public static final String STOMP_CONNECTS = "bidlive.stomp.connects";
	public static final String STOMP_SUBSCRIPTION_DENIALS =
		"bidlive.stomp.subscription.denials";
	public static final String CHAT_MESSAGES = "bidlive.chat.messages";
	public static final String CHAT_PROCESSING_DURATION =
		"bidlive.chat.processing.duration";
	public static final String REDIS_ERRORS = "bidlive.redis.errors";
	public static final String LLM_RETRIES = "bidlive.llm.moderation.retries";
	public static final String LLM_DEAD_LETTERS =
		"bidlive.llm.moderation.dead.letters";

	private final MeterRegistry meterRegistry;
	private final AtomicInteger activeStompSessions = new AtomicInteger();

	public RealtimeObservationMetrics(MeterRegistry meterRegistry) {
		this.meterRegistry = Objects.requireNonNull(
			meterRegistry,
			"meterRegistry"
		);
		Gauge.builder(
			ACTIVE_STOMP_SESSIONS,
			activeStompSessions,
			AtomicInteger::get
		)
			.description("현재 연결된 인증 STOMP 세션 수")
			.register(meterRegistry);
	}

	public void setActiveStompSessions(int sessionCount) {
		if (sessionCount < 0) {
			throw new IllegalArgumentException(
				"sessionCount must not be negative"
			);
		}
		activeStompSessions.set(sessionCount);
	}

	public void recordConnectSucceeded() {
		counter(
			STOMP_CONNECTS,
			"outcome",
			"success",
			"reason",
			"none"
		).increment();
	}

	public void recordConnectFailed(ConnectFailure failure) {
		counter(
			STOMP_CONNECTS,
			"outcome",
			"failure",
			"reason",
			require(failure).tagValue
		).increment();
	}

	public void recordSubscriptionDenied() {
		counter(
			STOMP_SUBSCRIPTION_DENIALS,
			"reason",
			"policy"
		).increment();
	}

	public void recordChatSend(
		Duration duration,
		ChatSendOutcome outcome
	) {
		Objects.requireNonNull(duration, "duration");
		if (duration.isNegative()) {
			throw new IllegalArgumentException("duration must not be negative");
		}
		String outcomeTag = require(outcome).tagValue;
		counter(CHAT_MESSAGES, "outcome", outcomeTag).increment();
		Timer.builder(CHAT_PROCESSING_DURATION)
			.description("STOMP 채팅 요청 처리 시간")
			.tag("outcome", outcomeTag)
			.register(meterRegistry)
			.record(duration);
	}

	public void recordRedisError(RedisOperation operation) {
		counter(
			REDIS_ERRORS,
			"operation",
			require(operation).tagValue
		).increment();
	}

	public void recordLlmRetry() {
		counter(LLM_RETRIES, "result", "scheduled").increment();
	}

	public void recordLlmDeadLetter() {
		counter(LLM_DEAD_LETTERS, "result", "stored").increment();
	}

	private Counter counter(String name, String... tags) {
		return Counter.builder(name)
			.tags(tags)
			.register(meterRegistry);
	}

	private static <T> T require(T value) {
		return Objects.requireNonNull(value, "metric tag value");
	}

	public enum ConnectFailure {
		EXPIRED("expired"),
		INVALID("invalid");

		private final String tagValue;

		ConnectFailure(String tagValue) {
			this.tagValue = tagValue;
		}
	}

	public enum ChatSendOutcome {
		CREATED("created"),
		DEDUPLICATED("deduplicated"),
		REJECTED("rejected"),
		FAILED("failed");

		private final String tagValue;

		ChatSendOutcome(String tagValue) {
			this.tagValue = tagValue;
		}
	}

	public enum RedisOperation {
		VIEWER_RECORD("viewer-record"),
		VIEWER_REMOVE("viewer-remove");

		private final String tagValue;

		RedisOperation(String tagValue) {
			this.tagValue = tagValue;
		}
	}
}
