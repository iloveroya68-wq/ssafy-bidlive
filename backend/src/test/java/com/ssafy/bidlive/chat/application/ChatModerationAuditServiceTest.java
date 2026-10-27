package com.ssafy.bidlive.chat.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import com.ssafy.bidlive.chat.domain.ChatMessageId;
import com.ssafy.bidlive.chat.domain.MaskingCategory;
import org.junit.jupiter.api.Test;

/** 감사 서비스의 암호화·해시·보존기간 조합과 만료 위임을 검증합니다. */
class ChatModerationAuditServiceTest {

	private static final Instant CREATED_AT =
		Instant.parse("2026-08-02T01:00:00Z");
	private final ChatModerationAuditCipher cipher =
		mock(ChatModerationAuditCipher.class);
	private final ChatModerationContentHasher hasher =
		mock(ChatModerationContentHasher.class);
	private final ChatModerationAuditPersistence persistence =
		mock(ChatModerationAuditPersistence.class);
	private final ChatModerationAuditService service =
		new ChatModerationAuditService(
			cipher,
			hasher,
			persistence,
			Duration.ofDays(30)
		);

	@Test
	void encryptsHashesAndStoresWithRetentionDeadline() {
		ChatModerationAuditRequestedEvent event = event();
		EncryptedChatContent encrypted =
			new EncryptedChatContent("cipher", "iv", "v1");
		when(cipher.encrypt(event.context(), event.originalContent()))
			.thenReturn(encrypted);
		when(hasher.hash(event.originalContent())).thenReturn("a".repeat(64));

		service.record(event);

		verify(persistence).saveIfAbsent(new ChatModerationAuditEntry(
			event.context(),
			encrypted,
			"a".repeat(64),
			event.maskedFields(),
			CREATED_AT,
			CREATED_AT.plus(Duration.ofDays(30))
		));
	}

	@Test
	void delegatesExpirationCleanupAndRejectsInvalidRetention() {
		service.deleteExpired(CREATED_AT);
		verify(persistence).deleteExpired(CREATED_AT);

		assertThatThrownBy(() -> new ChatModerationAuditService(
			cipher,
			hasher,
			persistence,
			Duration.ZERO
		)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("retention must be positive");
	}

	private static ChatModerationAuditRequestedEvent event() {
		return new ChatModerationAuditRequestedEvent(
			new ChatModerationAuditContext(
				new ChatMessageId("01J8W3P2N9KQ4T123456789ABC"),
				42L,
				7L
			),
			"카톡 abc123",
			Set.of(MaskingCategory.CONTACT),
			CREATED_AT
		);
	}
}
