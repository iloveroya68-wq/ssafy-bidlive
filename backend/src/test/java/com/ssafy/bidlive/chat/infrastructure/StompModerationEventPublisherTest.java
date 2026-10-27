package com.ssafy.bidlive.chat.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.time.Instant;

import com.ssafy.bidlive.chat.application.ChatModerationReason;
import com.ssafy.bidlive.chat.domain.ChatMessageId;
import com.ssafy.bidlive.chat.domain.ChatMutedEvent;
import com.ssafy.bidlive.chat.domain.ModerationWarningEvent;
import com.ssafy.bidlive.common.event.WsEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

/** 개인 moderation queue의 경고·뮤트 envelope와 공개 필드를 검증합니다. */
class StompModerationEventPublisherTest {

	private final SimpMessagingTemplate template =
		mock(SimpMessagingTemplate.class);
	private final StompModerationEventPublisher publisher =
		new StompModerationEventPublisher(template);

	@Test
	void sendsWarningOnlyToTargetUser() {
		publisher.onWarning(new ModerationWarningEvent(
			7L,
			new ChatMessageId("01J8W3P2N9KQ4T123456789ABC"),
			ChatModerationReason.OFF_PLATFORM_TRADE
		));

		ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
		verify(template).convertAndSendToUser(
			eq("7"),
			eq("/queue/moderation"),
			payload.capture()
		);
		assertThat(((WsEvent<?>) payload.getValue()).type())
			.isEqualTo("MODERATION_WARNING");
	}

	@Test
	void sendsMuteDurationAndExpiry() {
		Instant until = Instant.parse("2026-08-02T01:10:00Z");
		publisher.onMuted(new ChatMutedEvent(
			7L,
			42L,
			Duration.ofMinutes(10),
			until
		));

		ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
		verify(template).convertAndSendToUser(
			eq("7"),
			eq("/queue/moderation"),
			payload.capture()
		);
		@SuppressWarnings("unchecked")
		WsEvent<StompModerationEventPublisher.MutedData> event =
			(WsEvent<StompModerationEventPublisher.MutedData>) payload.getValue();
		assertThat(event.type()).isEqualTo("CHAT_MUTED");
		assertThat(event.data().durationSeconds()).isEqualTo(600L);
		assertThat(event.data().mutedUntil()).isEqualTo(until);
	}
}
