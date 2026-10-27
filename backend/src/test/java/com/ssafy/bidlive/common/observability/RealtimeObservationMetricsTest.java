package com.ssafy.bidlive.common.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Set;
import java.util.stream.Collectors;

import com.ssafy.bidlive.common.observability.RealtimeObservationMetrics.ChatSendOutcome;
import com.ssafy.bidlive.common.observability.RealtimeObservationMetrics.ConnectFailure;
import com.ssafy.bidlive.common.observability.RealtimeObservationMetrics.RedisOperation;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 실시간 운영 지표의 값과 저카디널리티 태그 구성을 검증합니다. */
class RealtimeObservationMetricsTest {

	private SimpleMeterRegistry registry;
	private RealtimeObservationMetrics metrics;

	@BeforeEach
	void setUp() {
		registry = new SimpleMeterRegistry();
		metrics = new RealtimeObservationMetrics(registry);
	}

	@Test
	void recordsActiveSessionsAndConnectOutcomes() {
		metrics.setActiveStompSessions(2);
		metrics.recordConnectSucceeded();
		metrics.recordConnectFailed(ConnectFailure.EXPIRED);

		assertThat(registry.get(
			RealtimeObservationMetrics.ACTIVE_STOMP_SESSIONS
		).gauge().value()).isEqualTo(2.0);
		assertThat(registry.get(RealtimeObservationMetrics.STOMP_CONNECTS)
			.tags("outcome", "success", "reason", "none")
			.counter().count()).isEqualTo(1.0);
		assertThat(registry.get(RealtimeObservationMetrics.STOMP_CONNECTS)
			.tags("outcome", "failure", "reason", "expired")
			.counter().count()).isEqualTo(1.0);
	}

	@Test
	void recordsChatCountDurationAndOperationalFailures() {
		metrics.recordSubscriptionDenied();
		metrics.recordChatSend(Duration.ofMillis(25), ChatSendOutcome.CREATED);
		metrics.recordRedisError(RedisOperation.VIEWER_RECORD);
		metrics.recordLlmRetry();
		metrics.recordLlmDeadLetter();

		assertThat(registry.get(
			RealtimeObservationMetrics.STOMP_SUBSCRIPTION_DENIALS
		).counter().count()).isEqualTo(1.0);
		assertThat(registry.get(RealtimeObservationMetrics.CHAT_MESSAGES)
			.tag("outcome", "created").counter().count()).isEqualTo(1.0);
		assertThat(registry.get(
			RealtimeObservationMetrics.CHAT_PROCESSING_DURATION
		).tag("outcome", "created").timer().totalTime(
			java.util.concurrent.TimeUnit.MILLISECONDS
		)).isEqualTo(25.0);
		assertThat(registry.get(RealtimeObservationMetrics.REDIS_ERRORS)
			.tag("operation", "viewer-record").counter().count())
			.isEqualTo(1.0);
		assertThat(registry.get(RealtimeObservationMetrics.LLM_RETRIES)
			.counter().count()).isEqualTo(1.0);
		assertThat(registry.get(RealtimeObservationMetrics.LLM_DEAD_LETTERS)
			.counter().count()).isEqualTo(1.0);
	}

	@Test
	void exposesOnlyBoundedTagKeys() {
		metrics.recordConnectFailed(ConnectFailure.INVALID);
		metrics.recordChatSend(Duration.ZERO, ChatSendOutcome.REJECTED);
		metrics.recordRedisError(RedisOperation.VIEWER_REMOVE);

		Set<String> tagKeys = registry.getMeters().stream()
			.flatMap(meter -> meter.getId().getTags().stream())
			.map(io.micrometer.core.instrument.Tag::getKey)
			.collect(Collectors.toSet());

		assertThat(tagKeys).containsOnly("outcome", "reason", "operation");
		assertThat(registry.getMeters())
			.extracting(Meter::getId)
			.noneMatch(id -> id.getTags().stream().anyMatch(tag ->
				Set.of(
					"memberId",
					"auctionId",
					"stompSessionId",
					"messageId"
				).contains(tag.getKey())
			));
	}
}
