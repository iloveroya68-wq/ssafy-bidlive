package com.ssafy.bidlive.chat.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Set;

import com.ssafy.bidlive.chat.domain.ChatMessage;
import com.ssafy.bidlive.chat.domain.ChatMessageContent;
import com.ssafy.bidlive.chat.domain.ChatMessageCreatedEvent;
import com.ssafy.bidlive.chat.domain.ChatMessageId;
import com.ssafy.bidlive.chat.domain.ClientMessageId;
import com.ssafy.bidlive.chat.domain.MaskingCategory;
import com.ssafy.bidlive.common.event.WsEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 커밋된 신규 채팅의 destination·envelope·전체 payload 송출 계약을 검증한다. */
class StompChatMessageEventPublisherTest {

	private static final Instant CREATED_AT =
		Instant.parse("2026-07-31T04:00:00Z");

	private final SimpMessagingTemplate messagingTemplate =
		mock(SimpMessagingTemplate.class);
	private final StompChatMessageEventPublisher publisher =
		new StompChatMessageEventPublisher(messagingTemplate);

	@Test
	void publishesChatMessageEnvelopeWithCompletePayload() {
		publisher.onCreated(new ChatMessageCreatedEvent(message()));

		ArgumentCaptor<Object> payload =
			ArgumentCaptor.forClass(Object.class);
		verify(messagingTemplate).convertAndSend(
			eq("/topic/auctions/100/chat"),
			payload.capture()
		);

		@SuppressWarnings("unchecked")
		WsEvent<StompChatMessageEventPublisher.ChatMessageData> event =
			(WsEvent<StompChatMessageEventPublisher.ChatMessageData>)
				payload.getValue();
		assertThat(event.type()).isEqualTo("CHAT_MESSAGE");
		assertThat(event.data().messageId())
			.isEqualTo("01J8W3P2N9KQ4T123456789ABC");
		assertThat(event.data().clientMessageId())
			.isEqualTo("018f6f4e-2a61-7c00-a1b2-334455667788");
		assertThat(event.data().memberId()).isEqualTo(25L);
		assertThat(event.data().nickname()).isEqualTo("경매참여자");
		assertThat(event.data().content()).isEqualTo("연락은 *************");
		assertThat(event.data().isQuestion()).isTrue();
		assertThat(event.data().maskedFields()).containsExactly(
			MaskingCategory.PROHIBITED_WORD,
			MaskingCategory.CONTACT
		);
		assertThat(event.data().createdAt()).isEqualTo(CREATED_AT);
	}

	@Test
	void listensOnlyAfterTransactionCommit() throws NoSuchMethodException {
		Method listener = StompChatMessageEventPublisher.class.getMethod(
			"onCreated",
			ChatMessageCreatedEvent.class
		);

		TransactionalEventListener annotation =
			listener.getAnnotation(TransactionalEventListener.class);
		assertThat(annotation).isNotNull();
		assertThat(annotation.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
	}

	private static ChatMessage message() {
		return new ChatMessage(
			new ChatMessageId("01J8W3P2N9KQ4T123456789ABC"),
			new ClientMessageId(
				"018f6f4e-2a61-7c00-a1b2-334455667788"
			),
			100L,
			25L,
			"경매참여자",
			new ChatMessageContent("연락은 *************"),
			true,
			Set.of(
				MaskingCategory.CONTACT,
				MaskingCategory.PROHIBITED_WORD
			),
			CREATED_AT
		);
	}
}
