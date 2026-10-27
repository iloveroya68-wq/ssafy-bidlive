package com.ssafy.bidlive.chat.infrastructure;

import java.time.Duration;

import com.ssafy.bidlive.chat.application.ChatModerationAuditCipher;
import com.ssafy.bidlive.chat.application.ChatModerationAuditPersistence;
import com.ssafy.bidlive.chat.application.ChatModerationAuditService;
import com.ssafy.bidlive.chat.application.ChatModerationContentHasher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 검열 감사 기록 서비스를 암호화·해시·저장 계약과 기본 보존기간으로 조립합니다. */
@Configuration(proxyBeanMethods = false)
public class ChatModerationAuditConfiguration {

	@Bean
	ChatModerationAuditService chatModerationAuditService(
		ChatModerationAuditCipher cipher,
		ChatModerationContentHasher contentHasher,
		ChatModerationAuditPersistence persistence,
		@Value("${moderation.audit-retention-days:30}") long retentionDays
	) {
		return new ChatModerationAuditService(
			cipher,
			contentHasher,
			persistence,
			Duration.ofDays(retentionDays)
		);
	}
}
