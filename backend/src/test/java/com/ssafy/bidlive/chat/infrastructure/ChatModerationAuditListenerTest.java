package com.ssafy.bidlive.chat.infrastructure;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.Set;

import com.ssafy.bidlive.chat.application.ChatModerationAuditContext;
import com.ssafy.bidlive.chat.application.ChatModerationAuditRequestedEvent;
import com.ssafy.bidlive.chat.application.ChatModerationAuditService;
import com.ssafy.bidlive.chat.domain.ChatMessageId;
import com.ssafy.bidlive.chat.domain.MaskingCategory;
import org.junit.jupiter.api.Test;

/** 감사 기록 성공과 실패가 커밋된 채팅 흐름에 전파되지 않는지 검증합니다. */
class ChatModerationAuditListenerTest {

	private final ChatModerationAuditService service =
		mock(ChatModerationAuditService.class);
	private final ChatModerationAuditListener listener =
		new ChatModerationAuditListener(service);

	@Test
	void recordsRequestedAudit() {
		ChatModerationAuditRequestedEvent event = event();

		listener.onRequested(event);

		verify(service).record(event);
	}

	@Test
	void swallowsAuditFailureWithoutExposingOriginalInException() {
		ChatModerationAuditRequestedEvent event = event();
		doThrow(new IllegalStateException("storage failed"))
			.when(service).record(event);

		assertThatCode(() -> listener.onRequested(event))
			.doesNotThrowAnyException();
	}

	private static ChatModerationAuditRequestedEvent event() {
		return new ChatModerationAuditRequestedEvent(
			new ChatModerationAuditContext(
				new ChatMessageId("01J8W3P2N9KQ4T123456789ABC"),
				42L,
				7L
			),
			"카톡 abc123",
			Set.of(MaskingCategory.CONTACT),
			Instant.parse("2026-08-02T01:00:00Z")
		);
	}
}
