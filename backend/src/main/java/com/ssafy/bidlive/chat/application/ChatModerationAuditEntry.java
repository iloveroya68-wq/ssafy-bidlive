package com.ssafy.bidlive.chat.application;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

import com.ssafy.bidlive.chat.domain.MaskingCategory;

/** 암호화된 검열 원문과 탐지 메타데이터의 보존 단위를 표현합니다. */
public record ChatModerationAuditEntry(
	ChatModerationAuditContext context,
	EncryptedChatContent originalContent,
	String originalContentHash,
	Set<MaskingCategory> maskedFields,
	Instant createdAt,
	Instant expiresAt
) {

	private static final int SHA_256_HEX_LENGTH = 64;

	public ChatModerationAuditEntry {
		Objects.requireNonNull(context, "context");
		Objects.requireNonNull(originalContent, "originalContent");
		Objects.requireNonNull(maskedFields, "maskedFields");
		Objects.requireNonNull(createdAt, "createdAt");
		Objects.requireNonNull(expiresAt, "expiresAt");
		if (originalContentHash == null
			|| originalContentHash.length() != SHA_256_HEX_LENGTH
			|| !originalContentHash.matches("[0-9a-f]{64}")) {
			throw new IllegalArgumentException(
				"originalContentHash must be lowercase SHA-256 hex"
			);
		}
		maskedFields = Set.copyOf(maskedFields);
		if (maskedFields.isEmpty()) {
			throw new IllegalArgumentException("maskedFields must not be empty");
		}
		if (!expiresAt.isAfter(createdAt)) {
			throw new IllegalArgumentException("expiresAt must be after createdAt");
		}
	}
}
