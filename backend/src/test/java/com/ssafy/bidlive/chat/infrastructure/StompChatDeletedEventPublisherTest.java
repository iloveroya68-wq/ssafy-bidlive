package com.ssafy.bidlive.chat.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.ssafy.bidlive.chat.domain.ChatDeletedEvent;
import com.ssafy.bidlive.chat.domain.ChatMessageId;
import com.ssafy.bidlive.common.event.WsEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

/** 채팅 삭제 이벤트의 destination과 공개 payload를 검증합니다. */
class StompChatDeletedEventPublisherTest {

	private final SimpMessagingTemplate messagingTemplate =
		mock(SimpMessagingTemplate.class);
	private final StompChatDeletedEventPublisher publisher =
		new StompChatDeletedEventPublisher(messagingTemplate);

	@Test
	void publishesOnlyMessageIdInChatDeletedEnvelope() {
		String messageId = "01J8W3P2N9KQ4T123456789ABC";
		publisher.onDeleted(new ChatDeletedEvent(
			42L,
			new ChatMessageId(messageId)
		));

		ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
		verify(messagingTemplate).convertAndSend(
			eq("/topic/auctions/42/chat"),
			payload.capture()
		);
		@SuppressWarnings("unchecked")
		WsEvent<StompChatDeletedEventPublisher.ChatDeletedData> event =
			(WsEvent<StompChatDeletedEventPublisher.ChatDeletedData>)
				payload.getValue();
		assertThat(event.type()).isEqualTo("CHAT_DELETED");
		assertThat(event.data().messageId()).isEqualTo(messageId);
	}
}
