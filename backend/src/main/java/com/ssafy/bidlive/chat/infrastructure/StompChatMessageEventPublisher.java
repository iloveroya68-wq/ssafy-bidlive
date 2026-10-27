package com.ssafy.bidlive.chat.infrastructure;

import java.time.Instant;
import java.util.List;

import com.ssafy.bidlive.chat.domain.ChatMessage;
import com.ssafy.bidlive.chat.domain.ChatMessageCreatedEvent;
import com.ssafy.bidlive.chat.domain.MaskingCategory;
import com.ssafy.bidlive.common.event.WsEvent;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 커밋된 신규 채팅만 CHAT_MESSAGE envelope로 경매 채팅 구독자에게 전달한다. */
@Component
public class StompChatMessageEventPublisher {

	static final String DESTINATION_PATTERN = "/topic/auctions/%d/chat";
	static final String EVENT_TYPE = "CHAT_MESSAGE";

	private final SimpMessagingTemplate messagingTemplate;

	public StompChatMessageEventPublisher(
		SimpMessagingTemplate messagingTemplate
	) {
		this.messagingTemplate = messagingTemplate;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onCreated(ChatMessageCreatedEvent event) {
		ChatMessage message = event.message();
		messagingTemplate.convertAndSend(
			DESTINATION_PATTERN.formatted(message.auctionId()),
			new WsEvent<>(EVENT_TYPE, ChatMessageData.from(message))
		);
	}

	/** CHAT_MESSAGE data에 서버 메시지와 optimistic UI 결합 정보를 담는다. */
	record ChatMessageData(
		String messageId,
		String clientMessageId,
		long memberId,
		String nickname,
		String content,
		boolean isQuestion,
		List<MaskingCategory> maskedFields,
		Instant createdAt
	) {

		static ChatMessageData from(ChatMessage message) {
			return new ChatMessageData(
				message.messageId().value(),
				message.clientMessageId().value(),
				message.memberId(),
				message.nickname(),
				message.content().value(),
				message.isQuestion(),
				message.maskedFields().stream().sorted().toList(),
				message.createdAt()
			);
		}
	}
}
