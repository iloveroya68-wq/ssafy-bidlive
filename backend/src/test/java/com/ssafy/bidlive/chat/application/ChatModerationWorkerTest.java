package com.ssafy.bidlive.chat.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import com.ssafy.bidlive.chat.domain.ChatMessageId;
import com.ssafy.bidlive.common.observability.RealtimeObservationMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 비동기 검열 worker의 성공·백오프 재시도·DLQ 전이를 검증합니다. */
class ChatModerationWorkerTest {

	private static final Instant NOW = Instant.parse("2026-08-02T01:00:00Z");
	private ChatModerationJobQueue queue;
	private ChatLlmModerationGateway gateway;
	private ChatModerationOutcomeHandler outcomeHandler;
	private RealtimeObservationMetrics metrics;
	private ChatModerationWorker worker;

	@BeforeEach
	void setUp() {
		queue = mock(ChatModerationJobQueue.class);
		gateway = mock(ChatLlmModerationGateway.class);
		outcomeHandler = mock(ChatModerationOutcomeHandler.class);
		metrics = mock(RealtimeObservationMetrics.class);
		worker = new ChatModerationWorker(
			queue,
			gateway,
			outcomeHandler,
			metrics,
			Clock.fixed(NOW, ZoneOffset.UTC),
			3,
			Duration.ofSeconds(2),
			Duration.ofMinutes(1)
		);
	}

	@Test
	void completesSuccessfulJudgementOnce() {
		ClaimedChatModerationJob claimed = claimed(1);
		ChatModerationJudgement judgement = new ChatModerationJudgement(
			ChatModerationDecision.ALLOW,
			ChatModerationReason.SAFE
		);
		when(queue.claimDue(NOW, Duration.ofMinutes(1), 3))
			.thenReturn(Optional.of(claimed));
		when(gateway.moderate(claimed.job())).thenReturn(judgement);

		assertThat(worker.processNext()).isTrue();

		verify(outcomeHandler).apply(claimed.job(), judgement);
		verify(queue).succeed(19L, NOW);
	}

	@Test
	void retriesRetryableFailureWithBackoffAndJitter() {
		ClaimedChatModerationJob claimed = claimed(1);
		when(queue.claimDue(NOW, Duration.ofMinutes(1), 3))
			.thenReturn(Optional.of(claimed));
		when(gateway.moderate(claimed.job())).thenThrow(
			new ChatLlmModerationException(
				ChatLlmFailureCode.LLM_TIMEOUT,
				true,
				null
			)
		);

		worker.processNext();

		verify(queue).retry(
			19L,
			NOW.plusMillis(2589L),
			ChatLlmFailureCode.LLM_TIMEOUT,
			NOW
		);
		verify(queue, never()).deadLetter(any(Long.class), any(), any());
		verify(metrics).recordLlmRetry();
	}

	@Test
	void deadLettersInvalidResponseAndExhaustedRetry() {
		ClaimedChatModerationJob invalid = claimed(1);
		when(queue.claimDue(NOW, Duration.ofMinutes(1), 3))
			.thenReturn(Optional.of(invalid));
		when(gateway.moderate(invalid.job())).thenThrow(
			new ChatLlmModerationException(
				ChatLlmFailureCode.LLM_RESPONSE_INVALID,
				false,
				null
			)
		);
		worker.processNext();
		verify(queue).deadLetter(
			19L,
			ChatLlmFailureCode.LLM_RESPONSE_INVALID,
			NOW
		);
		verify(metrics).recordLlmDeadLetter();

		ClaimedChatModerationJob exhausted = claimed(3);
		reset(gateway);
		when(queue.claimDue(NOW, Duration.ofMinutes(1), 3))
			.thenReturn(Optional.of(exhausted));
		when(gateway.moderate(exhausted.job())).thenThrow(
			new ChatLlmModerationException(
				ChatLlmFailureCode.LLM_TIMEOUT,
				true,
				null
			)
		);
		worker.processNext();
		verify(queue).deadLetter(
			19L,
			ChatLlmFailureCode.LLM_RETRY_EXHAUSTED,
			NOW
		);
	}

	private ClaimedChatModerationJob claimed(int attempt) {
		return new ClaimedChatModerationJob(
			19L,
			new ChatModerationJob(
				42L,
				new ChatMessageId("01J8W3P2N9KQ4T123456789ABC"),
				7L,
				"마스킹된 본문",
				false,
				NOW.minusSeconds(10)
			),
			attempt
		);
	}
}
