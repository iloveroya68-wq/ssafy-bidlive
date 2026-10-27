package com.ssafy.bidlive.chat.infrastructure;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import com.ssafy.bidlive.chat.application.ChatModerationAuditService;
import org.junit.jupiter.api.Test;

/** 감사 로그 만료 정리 시각과 실패 격리를 검증합니다. */
class ChatModerationAuditCleanupSchedulerTest {

	private static final Instant NOW =
		Instant.parse("2026-08-02T02:00:00Z");
	private final ChatModerationAuditService service =
		mock(ChatModerationAuditService.class);
	private final ChatModerationAuditCleanupScheduler scheduler =
		new ChatModerationAuditCleanupScheduler(
			service,
			Clock.fixed(NOW, ZoneOffset.UTC)
		);

	@Test
	void deletesExpiredRecordsAtCurrentTime() {
		scheduler.cleanup();

		verify(service).deleteExpired(NOW);
	}

	@Test
	void swallowsCleanupFailure() {
		doThrow(new IllegalStateException("database unavailable"))
			.when(service).deleteExpired(NOW);

		assertThatCode(scheduler::cleanup).doesNotThrowAnyException();
	}
}
