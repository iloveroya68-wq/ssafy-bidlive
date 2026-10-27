package com.ssafy.bidlive.chat.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.ssafy.bidlive.chat.application.ChatModerationAuditCipher;
import com.ssafy.bidlive.chat.application.ChatModerationAuditPersistence;
import com.ssafy.bidlive.chat.application.ChatModerationAuditService;
import com.ssafy.bidlive.chat.application.ChatModerationContentHasher;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** 감사 서비스 Bean의 기본 보존기간 구성과 잘못된 설정 차단을 검증합니다. */
class ChatModerationAuditConfigurationTest {

	private final ApplicationContextRunner contextRunner =
		new ApplicationContextRunner()
			.withBean(
				ChatModerationAuditCipher.class,
				() -> mock(ChatModerationAuditCipher.class)
			)
			.withBean(
				ChatModerationContentHasher.class,
				() -> mock(ChatModerationContentHasher.class)
			)
			.withBean(
				ChatModerationAuditPersistence.class,
				() -> mock(ChatModerationAuditPersistence.class)
			)
			.withUserConfiguration(ChatModerationAuditConfiguration.class);

	@Test
	void createsServiceWithDefaultRetention() {
		contextRunner.run(context ->
			assertThat(context).hasSingleBean(ChatModerationAuditService.class)
		);
	}

	@Test
	void rejectsNonPositiveRetentionDays() {
		contextRunner
			.withPropertyValues("moderation.audit-retention-days=0")
			.run(context -> {
				assertThat(context).hasFailed();
				assertThat(context.getStartupFailure())
					.hasRootCauseInstanceOf(IllegalArgumentException.class)
					.hasStackTraceContaining("retention must be positive");
			});
	}
}
