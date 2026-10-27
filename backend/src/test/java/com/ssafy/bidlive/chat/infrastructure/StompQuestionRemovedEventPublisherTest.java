package com.ssafy.bidlive.chat.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Method;

import com.ssafy.bidlive.chat.domain.ChatMessageId;
import com.ssafy.bidlive.chat.domain.QuestionRemovalReason;
import com.ssafy.bidlive.chat.domain.QuestionRemovedEvent;
import com.ssafy.bidlive.common.event.WsEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 질문 제거 이벤트의 destination·envelope·사유와 커밋 후 송출을 검증한다. */
class StompQuestionRemovedEventPublisherTest {

	private static final ChatMessageId QUESTION_ID =
		new ChatMessageId("01J8W3P2N9KQ4T123456789ABC");

	private final SimpMessagingTemplate messagingTemplate =
		mock(SimpMessagingTemplate.class);
	private final StompQuestionRemovedEventPublisher publisher =
		new StompQuestionRemovedEventPublisher(messagingTemplate);

	@ParameterizedTest
	@EnumSource(QuestionRemovalReason.class)
	void publishesQuestionRemovedEnvelopeWithReason(
		QuestionRemovalReason reason
	) {
		publisher.onRemoved(new QuestionRemovedEvent(100L, QUESTION_ID, reason));

		ArgumentCaptor<Object> payload =
			ArgumentCaptor.forClass(Object.class);
		verify(messagingTemplate).convertAndSend(
			eq("/topic/auctions/100/questions"),
			payload.capture()
		);

		@SuppressWarnings("unchecked")
		WsEvent<StompQuestionRemovedEventPublisher.QuestionRemovedData> event =
			(WsEvent<StompQuestionRemovedEventPublisher.QuestionRemovedData>)
				payload.getValue();
		assertThat(event.type()).isEqualTo("QUESTION_REMOVED");
		assertThat(event.data().questionId()).isEqualTo(QUESTION_ID.value());
		assertThat(event.data().reason()).isEqualTo(reason);
	}

	@Test
	void listensOnlyAfterTransactionCommit() throws NoSuchMethodException {
		Method listener = StompQuestionRemovedEventPublisher.class.getMethod(
			"onRemoved",
			QuestionRemovedEvent.class
		);

		TransactionalEventListener annotation =
			listener.getAnnotation(TransactionalEventListener.class);
		assertThat(annotation).isNotNull();
		assertThat(annotation.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
	}
}
