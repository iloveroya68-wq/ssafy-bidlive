package com.ssafy.bidlive.chat.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Set;

import com.ssafy.bidlive.chat.domain.ChatMessage;
import com.ssafy.bidlive.chat.domain.ChatMessageContent;
import com.ssafy.bidlive.chat.domain.ChatMessageCreatedEvent;
import com.ssafy.bidlive.chat.domain.ChatMessageId;
import com.ssafy.bidlive.chat.domain.ClientMessageId;
import com.ssafy.bidlive.common.event.WsEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 신규 질문의 판매자 토픽 송출과 일반 채팅 제외 조건을 검증한다. */
class StompQuestionAddedEventPublisherTest {

	private static final Instant CREATED_AT =
		Instant.parse("2026-07-31T04:00:00Z");

	private final SimpMessagingTemplate messagingTemplate =
		mock(SimpMessagingTemplate.class);
	private final StompQuestionAddedEventPublisher publisher =
		new StompQuestionAddedEventPublisher(messagingTemplate);

	@Test
	void publishesQuestionAddedEnvelopeToSellerTopic() {
		publisher.onCreated(new ChatMessageCreatedEvent(message(true)));

		ArgumentCaptor<Object> payload =
			ArgumentCaptor.forClass(Object.class);
		verify(messagingTemplate).convertAndSend(
			eq("/topic/auctions/100/questions"),
			payload.capture()
		);

		@SuppressWarnings("unchecked")
		WsEvent<StompQuestionAddedEventPublisher.QuestionAddedData> event =
			(WsEvent<StompQuestionAddedEventPublisher.QuestionAddedData>)
				payload.getValue();
		assertThat(event.type()).isEqualTo("QUESTION_ADDED");
		assertThat(event.data().questionId())
			.isEqualTo("01J8W3P2N9KQ4T123456789ABC");
		assertThat(event.data().memberId()).isEqualTo(25L);
		assertThat(event.data().nickname()).isEqualTo("경매참여자");
		assertThat(event.data().content()).isEqualTo("흠집을 보여주세요");
		assertThat(event.data().createdAt()).isEqualTo(CREATED_AT);
	}

	@Test
	void ignoresOrdinaryChatMessage() {
		publisher.onCreated(new ChatMessageCreatedEvent(message(false)));

		verify(messagingTemplate, never()).convertAndSend(
			eq("/topic/auctions/100/questions"),
			org.mockito.ArgumentMatchers.any(Object.class)
		);
	}

	@Test
	void listensOnlyAfterTransactionCommit() throws NoSuchMethodException {
		Method listener = StompQuestionAddedEventPublisher.class.getMethod(
			"onCreated",
			ChatMessageCreatedEvent.class
		);

		TransactionalEventListener annotation =
			listener.getAnnotation(TransactionalEventListener.class);
		assertThat(annotation).isNotNull();
		assertThat(annotation.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
	}

	private static ChatMessage message(boolean isQuestion) {
		return new ChatMessage(
			new ChatMessageId("01J8W3P2N9KQ4T123456789ABC"),
			new ClientMessageId(
				"018f6f4e-2a61-7c00-a1b2-334455667788"
			),
			100L,
			25L,
			"경매참여자",
			new ChatMessageContent("흠집을 보여주세요"),
			isQuestion,
			Set.of(),
			CREATED_AT
		);
	}
}
