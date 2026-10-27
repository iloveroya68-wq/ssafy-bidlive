package com.ssafy.bidlive.chat.infrastructure;

import com.ssafy.bidlive.chat.domain.QuestionRemovalReason;
import com.ssafy.bidlive.chat.domain.QuestionRemovedEvent;
import com.ssafy.bidlive.common.event.WsEvent;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 커밋된 질문 제거를 QUESTION_REMOVED envelope로 판매자 질문 토픽에 전달한다. */
@Component
public class StompQuestionRemovedEventPublisher {

	static final String DESTINATION_PATTERN =
		"/topic/auctions/%d/questions";
	static final String EVENT_TYPE = "QUESTION_REMOVED";

	private final SimpMessagingTemplate messagingTemplate;

	public StompQuestionRemovedEventPublisher(
		SimpMessagingTemplate messagingTemplate
	) {
		this.messagingTemplate = messagingTemplate;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onRemoved(QuestionRemovedEvent event) {
		messagingTemplate.convertAndSend(
			DESTINATION_PATTERN.formatted(event.auctionId()),
			new WsEvent<>(
				EVENT_TYPE,
				new QuestionRemovedData(
					event.questionId().value(),
					event.reason()
				)
			)
		);
	}

	/** QUESTION_REMOVED가 제거할 질문과 확정 사유를 담는다. */
	record QuestionRemovedData(
		String questionId,
		QuestionRemovalReason reason
	) {
	}
}
