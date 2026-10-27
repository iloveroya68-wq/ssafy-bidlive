package com.ssafy.bidlive.chat.infrastructure;

import java.time.Instant;

import com.ssafy.bidlive.chat.domain.ChatMessage;
import com.ssafy.bidlive.chat.domain.ChatMessageCreatedEvent;
import com.ssafy.bidlive.common.event.WsEvent;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 커밋된 신규 질문만 QUESTION_ADDED envelope로 판매자 질문 토픽에 전달한다. */
@Component
public class StompQuestionAddedEventPublisher {

	static final String DESTINATION_PATTERN =
		"/topic/auctions/%d/questions";
	static final String EVENT_TYPE = "QUESTION_ADDED";

	private final SimpMessagingTemplate messagingTemplate;

	public StompQuestionAddedEventPublisher(
		SimpMessagingTemplate messagingTemplate
	) {
		this.messagingTemplate = messagingTemplate;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onCreated(ChatMessageCreatedEvent event) {
		ChatMessage message = event.message();
		if (!message.isQuestion()) {
			return;
		}

		messagingTemplate.convertAndSend(
			DESTINATION_PATTERN.formatted(message.auctionId()),
			new WsEvent<>(EVENT_TYPE, QuestionAddedData.from(message))
		);
	}

	/** QUESTION_ADDED가 판매자에게 전달할 질문 식별자와 작성자 정보를 담는다. */
	record QuestionAddedData(
		String questionId,
		long memberId,
		String nickname,
		String content,
		Instant createdAt
	) {

		static QuestionAddedData from(ChatMessage message) {
			return new QuestionAddedData(
				message.messageId().value(),
				message.memberId(),
				message.nickname(),
				message.content().value(),
				message.createdAt()
			);
		}
	}
}
