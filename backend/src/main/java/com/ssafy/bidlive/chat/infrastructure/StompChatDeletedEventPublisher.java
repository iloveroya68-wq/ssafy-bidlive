package com.ssafy.bidlive.chat.infrastructure;

import com.ssafy.bidlive.chat.domain.ChatDeletedEvent;
import com.ssafy.bidlive.common.event.WsEvent;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 커밋된 채팅 삭제 사실을 경매 채팅 구독자에게 전달합니다. */
@Component
public class StompChatDeletedEventPublisher {

	static final String DESTINATION_PATTERN = "/topic/auctions/%d/chat";
	static final String EVENT_TYPE = "CHAT_DELETED";

	private final SimpMessagingTemplate messagingTemplate;

	public StompChatDeletedEventPublisher(
		SimpMessagingTemplate messagingTemplate
	) {
		this.messagingTemplate = messagingTemplate;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onDeleted(ChatDeletedEvent event) {
		messagingTemplate.convertAndSend(
			DESTINATION_PATTERN.formatted(event.auctionId()),
			new WsEvent<>(
				EVENT_TYPE,
				new ChatDeletedData(event.messageId().value())
			)
		);
	}

	/** CHAT_DELETED가 클라이언트에서 제거할 공개 메시지 ULID를 전달합니다. */
	record ChatDeletedData(String messageId) {
	}
}
