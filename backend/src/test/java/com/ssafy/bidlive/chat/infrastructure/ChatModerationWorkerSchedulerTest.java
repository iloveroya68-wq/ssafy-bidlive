package com.ssafy.bidlive.chat.infrastructure;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import com.ssafy.bidlive.chat.application.ChatModerationJobQueue;
import com.ssafy.bidlive.chat.application.ChatModerationWorker;
import org.junit.jupiter.api.Test;

/** scheduler의 batch 상한과 최종 임대 복구 호출을 검증합니다. */
class ChatModerationWorkerSchedulerTest {

	@Test
	void recoversExpiredLeasesAndStopsWhenQueueIsEmpty() {
		Instant now = Instant.parse("2026-08-02T01:00:00Z");
		ChatModerationWorker worker = mock(ChatModerationWorker.class);
		ChatModerationJobQueue queue = mock(ChatModerationJobQueue.class);
		ChatAsyncModerationProperties properties = properties();
		when(worker.processNext()).thenReturn(true, true, false);
		ChatModerationWorkerScheduler scheduler =
			new ChatModerationWorkerScheduler(
				worker,
				queue,
				properties,
				Clock.fixed(now, ZoneOffset.UTC)
			);

		scheduler.poll();

		verify(queue).recoverExhaustedLeases(
			now.minus(Duration.ofMinutes(1)),
			3,
			now
		);
		verify(worker, times(3)).processNext();
	}

	private ChatAsyncModerationProperties properties() {
		ChatAsyncModerationProperties properties =
			new ChatAsyncModerationProperties();
		properties.setLease(Duration.ofMinutes(1));
		properties.setMaxAttempts(3);
		properties.setBatchSize(10);
		return properties;
	}
}
