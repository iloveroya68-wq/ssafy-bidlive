package com.ssafy.bidlive.chat.infrastructure.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import com.ssafy.bidlive.chat.application.ChatModerationAuditCipher;
import com.ssafy.bidlive.chat.application.ChatModerationAuditContext;
import com.ssafy.bidlive.chat.application.EncryptedChatContent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** AES-256-GCM으로 검열 원문의 기밀성과 채팅 문맥 무결성을 보호합니다. */
@Component
public class AesGcmChatModerationAuditCipher
	implements ChatModerationAuditCipher {

	private static final String TRANSFORMATION = "AES/GCM/NoPadding";
	private static final String KEY_ALGORITHM = "AES";
	private static final int REQUIRED_KEY_BYTES = 32;
	private static final int IV_BYTES = 12;
	private static final int AUTHENTICATION_TAG_BITS = 128;

	private final String encodedKey;
	private final String keyVersion;
	private final SecureRandom secureRandom;

	@Autowired
	public AesGcmChatModerationAuditCipher(
		@Value("${CHAT_MODERATION_AUDIT_ENCRYPTION_KEY_BASE64:}")
		String encodedKey,
		@Value("${CHAT_MODERATION_AUDIT_ENCRYPTION_KEY_VERSION:v1}")
		String keyVersion
	) {
		this(encodedKey, keyVersion, new SecureRandom());
	}

	AesGcmChatModerationAuditCipher(
		String encodedKey,
		String keyVersion,
		SecureRandom secureRandom
	) {
		this.encodedKey = encodedKey;
		this.keyVersion = keyVersion;
		this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom");
	}

	@Override
	public EncryptedChatContent encrypt(
		ChatModerationAuditContext context,
		String originalContent
	) {
		Objects.requireNonNull(context, "context");
		if (originalContent == null || originalContent.isEmpty()) {
			throw new IllegalArgumentException(
				"originalContent must not be empty"
			);
		}
		try {
			byte[] iv = new byte[IV_BYTES];
			secureRandom.nextBytes(iv);
			Cipher cipher = cipher(Cipher.ENCRYPT_MODE, iv);
			cipher.updateAAD(aad(context));
			byte[] ciphertext = cipher.doFinal(
				originalContent.getBytes(StandardCharsets.UTF_8)
			);
			return new EncryptedChatContent(
				Base64.getEncoder().encodeToString(ciphertext),
				Base64.getEncoder().encodeToString(iv),
				validatedKeyVersion()
			);
		} catch (GeneralSecurityException | IllegalArgumentException exception) {
			throw encryptionFailure(exception);
		}
	}

	@Override
	public String decrypt(
		ChatModerationAuditContext context,
		EncryptedChatContent encryptedContent
	) {
		Objects.requireNonNull(context, "context");
		Objects.requireNonNull(encryptedContent, "encryptedContent");
		try {
			if (!validatedKeyVersion().equals(encryptedContent.keyVersion())) {
				throw new IllegalStateException(
					"chat moderation audit encryption key version is unavailable"
				);
			}
			byte[] iv = Base64.getDecoder().decode(encryptedContent.iv());
			if (iv.length != IV_BYTES) {
				throw new IllegalStateException(
					"chat moderation audit encryption IV is invalid"
				);
			}
			Cipher cipher = cipher(Cipher.DECRYPT_MODE, iv);
			cipher.updateAAD(aad(context));
			byte[] plaintext = cipher.doFinal(
				Base64.getDecoder().decode(encryptedContent.ciphertext())
			);
			return new String(plaintext, StandardCharsets.UTF_8);
		} catch (GeneralSecurityException | IllegalArgumentException exception) {
			throw encryptionFailure(exception);
		}
	}

	private Cipher cipher(int mode, byte[] iv)
		throws GeneralSecurityException {
		Cipher cipher = Cipher.getInstance(TRANSFORMATION);
		cipher.init(
			mode,
			secretKey(),
			new GCMParameterSpec(AUTHENTICATION_TAG_BITS, iv)
		);
		return cipher;
	}

	private SecretKey secretKey() {
		if (encodedKey == null || encodedKey.isBlank()) {
			throw new IllegalStateException(
				"chat moderation audit encryption key is unavailable"
			);
		}
		byte[] key;
		try {
			key = Base64.getDecoder().decode(encodedKey);
		} catch (IllegalArgumentException exception) {
			throw new IllegalStateException(
				"chat moderation audit encryption key is invalid",
				exception
			);
		}
		if (key.length != REQUIRED_KEY_BYTES) {
			throw new IllegalStateException(
				"chat moderation audit encryption key must be 32 bytes"
			);
		}
		return new SecretKeySpec(key, KEY_ALGORITHM);
	}

	private String validatedKeyVersion() {
		if (keyVersion == null || keyVersion.isBlank()) {
			throw new IllegalStateException(
				"chat moderation audit encryption key version is unavailable"
			);
		}
		return keyVersion.strip();
	}

	private static byte[] aad(ChatModerationAuditContext context) {
		return ("chat-moderation-audit:"
			+ context.messageId().value()
			+ ":" + context.auctionId()
			+ ":" + context.memberId())
			.getBytes(StandardCharsets.UTF_8);
	}

	private static IllegalStateException encryptionFailure(Throwable cause) {
		if (cause instanceof IllegalStateException illegalStateException) {
			return illegalStateException;
		}
		return new IllegalStateException(
			"chat moderation audit encryption operation failed",
			cause
		);
	}
}
