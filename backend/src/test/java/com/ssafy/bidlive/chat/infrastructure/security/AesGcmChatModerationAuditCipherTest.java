package com.ssafy.bidlive.chat.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.SecureRandom;
import java.util.Base64;

import com.ssafy.bidlive.chat.application.ChatModerationAuditContext;
import com.ssafy.bidlive.chat.application.EncryptedChatContent;
import com.ssafy.bidlive.chat.domain.ChatMessageId;
import org.junit.jupiter.api.Test;

/** 검열 원문의 AES-GCM 암복호화와 문맥 결합·키 검증을 확인합니다. */
class AesGcmChatModerationAuditCipherTest {

	private static final String KEY_BASE64 = Base64.getEncoder()
		.encodeToString("0123456789abcdef0123456789abcdef".getBytes());

	@Test
	void encryptsWithoutPlaintextAndDecryptsInSameContext() {
		AesGcmChatModerationAuditCipher cipher = cipher(KEY_BASE64, "v1");
		String original = "카톡 abc123으로 연락 주세요";

		EncryptedChatContent encrypted = cipher.encrypt(context(42L), original);

		assertThat(encrypted.ciphertext()).doesNotContain(original);
		assertThat(encrypted.keyVersion()).isEqualTo("v1");
		assertThat(cipher.decrypt(context(42L), encrypted)).isEqualTo(original);
	}

	@Test
	void rejectsDecryptionInDifferentAuctionContext() {
		AesGcmChatModerationAuditCipher cipher = cipher(KEY_BASE64, "v1");
		EncryptedChatContent encrypted = cipher.encrypt(context(42L), "원문");

		assertThatThrownBy(() -> cipher.decrypt(context(43L), encrypted))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("chat moderation audit encryption operation failed");
	}

	@Test
	void rejectsMissingOrWrongSizedKeyWithoutExposingContent() {
		String original = "노출되면 안 되는 원문";

		assertThatThrownBy(() -> cipher("", "v1").encrypt(context(42L), original))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("chat moderation audit encryption key is unavailable")
			.hasMessageNotContaining(original);
		assertThatThrownBy(() -> cipher(
			Base64.getEncoder().encodeToString(new byte[16]),
			"v1"
		).encrypt(context(42L), original))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("chat moderation audit encryption key must be 32 bytes")
			.hasMessageNotContaining(original);
	}

	private static AesGcmChatModerationAuditCipher cipher(
		String key,
		String version
	) {
		return new AesGcmChatModerationAuditCipher(
			key,
			version,
			new SecureRandom()
		);
	}

	private static ChatModerationAuditContext context(long auctionId) {
		return new ChatModerationAuditContext(
			new ChatMessageId("01J8W3P2N9KQ4T123456789ABC"),
			auctionId,
			7L
		);
	}
}
