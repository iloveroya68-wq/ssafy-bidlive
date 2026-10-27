package com.ssafy.bidlive.chat.infrastructure;

import java.time.Instant;

import com.ssafy.bidlive.chat.application.ChatModerationReason;
import com.ssafy.bidlive.chat.domain.ChatMutedEvent;
import com.ssafy.bidlive.chat.domain.ModerationWarningEvent;
import com.ssafy.bidlive.common.event.WsEvent;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 검열 경고와 뮤트를 판정 대상 사용자의 개인 moderation queue로 전송합니다. */
@Component
public class StompModerationEventPublisher {

	private static final String DESTINATION = "/queue/moderation";
	private final SimpMessagingTemplate messagingTemplate;

	public StompModerationEventPublisher(
		SimpMessagingTemplate messagingTemplate
	) {
		this.messagingTemplate = messagingTemplate;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onWarning(ModerationWarningEvent event) {
		messagingTemplate.convertAndSendToUser(
			Long.toString(event.memberId()),
			DESTINATION,
			new WsEvent<>("MODERATION_WARNING", new WarningData(
				event.messageId().value(),
				event.reason(),
				"채팅 운영 정책 위반 가능성이 감지되었습니다."
			))
		);
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onMuted(ChatMutedEvent event) {
		messagingTemplate.convertAndSendToUser(
			Long.toString(event.memberId()),
			DESTINATION,
			new WsEvent<>("CHAT_MUTED", new MutedData(
				event.auctionId(),
				event.duration().toSeconds(),
				event.mutedUntil()
			))
		);
	}

	record WarningData(
		String messageId,
		ChatModerationReason reason,
		String message
	) {
	}

	record MutedData(
		long auctionId,
		long durationSeconds,
		Instant mutedUntil
	) {
	}
}
