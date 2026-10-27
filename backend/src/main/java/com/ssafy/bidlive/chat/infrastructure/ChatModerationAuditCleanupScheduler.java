package com.ssafy.bidlive.chat.infrastructure;

import java.time.Clock;
import java.util.Objects;

import com.ssafy.bidlive.chat.application.ChatModerationAuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 보존기간이 지난 검열 감사 레코드를 주기적으로 정리합니다. */
@Component
public class ChatModerationAuditCleanupScheduler {

	private static final Logger log = LoggerFactory.getLogger(
		ChatModerationAuditCleanupScheduler.class
	);
	private final ChatModerationAuditService auditService;
	private final Clock clock;

	@Autowired
	public ChatModerationAuditCleanupScheduler(
		ChatModerationAuditService auditService
	) {
		this(auditService, Clock.systemUTC());
	}

	ChatModerationAuditCleanupScheduler(
		ChatModerationAuditService auditService,
		Clock clock
	) {
		this.auditService = Objects.requireNonNull(auditService, "auditService");
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	@Scheduled(fixedDelayString = "${moderation.audit-cleanup-delay:1h}")
	public void cleanup() {
		try {
			int deleted = auditService.deleteExpired(clock.instant());
			if (deleted > 0) {
				log.info("expired chat moderation audit records deleted: count={}", deleted);
			}
		} catch (RuntimeException exception) {
			log.error(
				"chat moderation audit cleanup failed: failureType={}",
				exception.getClass().getSimpleName()
			);
		}
	}
}
