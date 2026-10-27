package com.ssafy.bidlive.chat.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import com.ssafy.bidlive.chat.domain.ChatMessageId;
import com.ssafy.bidlive.chat.domain.MaskingCategory;
import org.junit.jupiter.api.Test;

/** 감사 레코드가 암호문·탐지 유형·보존기간 불변식을 보호하는지 검증합니다. */
class ChatModerationAuditEntryTest {

	private static final Instant CREATED_AT =
		Instant.parse("2026-08-02T12:00:00Z");

	@Test
	void copiesMaskedFieldsAndAcceptsValidRetentionWindow() {
		Set<MaskingCategory> fields = new HashSet<>();
		fields.add(MaskingCategory.CONTACT);

		ChatModerationAuditEntry entry = entry(fields);
		fields.clear();

		assertThat(entry.maskedFields())
			.containsExactly(MaskingCategory.CONTACT);
		assertThat(entry.expiresAt()).isEqualTo(CREATED_AT.plusSeconds(60));
	}

	@Test
	void rejectsEmptyMaskedFields() {
		assertThatThrownBy(() -> entry(Set.of()))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("maskedFields must not be empty");
	}

	@Test
	void rejectsInvalidHashAndRetentionWindow() {
		assertThatThrownBy(() -> new ChatModerationAuditEntry(
			context(),
			new EncryptedChatContent("cipher", "iv", "v1"),
			"not-a-hash",
			Set.of(MaskingCategory.ACCOUNT),
			CREATED_AT,
			CREATED_AT.plusSeconds(1)
		)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("originalContentHash must be lowercase SHA-256 hex");

		assertThatThrownBy(() -> new ChatModerationAuditEntry(
			context(),
			new EncryptedChatContent("cipher", "iv", "v1"),
			"a".repeat(64),
			Set.of(MaskingCategory.ACCOUNT),
			CREATED_AT,
			CREATED_AT
		)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("expiresAt must be after createdAt");
	}

	private static ChatModerationAuditEntry entry(
		Set<MaskingCategory> fields
	) {
		return new ChatModerationAuditEntry(
			context(),
			new EncryptedChatContent("cipher", "iv", "v1"),
			"a".repeat(64),
			fields,
			CREATED_AT,
			CREATED_AT.plusSeconds(60)
		);
	}

	private static ChatModerationAuditContext context() {
		return new ChatModerationAuditContext(
			new ChatMessageId("01J8W3P2N9KQ4T123456789ABC"),
			42L,
			7L
		);
	}
}
