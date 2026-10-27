package com.ssafy.bidlive.chat.infrastructure;

import com.ssafy.bidlive.chat.application.ChatModerationAuditRequestedEvent;
import com.ssafy.bidlive.chat.application.ChatModerationAuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 채팅 커밋 이후 감사 기록을 수행하고 실패를 채팅 트랜잭션과 격리합니다. */
@Component
public class ChatModerationAuditListener {

	private static final Logger log = LoggerFactory.getLogger(
		ChatModerationAuditListener.class
	);
	private final ChatModerationAuditService auditService;

	public ChatModerationAuditListener(
		ChatModerationAuditService auditService
	) {
		this.auditService = auditService;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onRequested(ChatModerationAuditRequestedEvent event) {
		try {
			auditService.record(event);
		} catch (RuntimeException exception) {
			log.error(
				"chat moderation audit recording failed: failureType={}",
				exception.getClass().getSimpleName()
			);
		}
	}
}
