package com.ssafy.bidlive.chat.application;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** 검열 원문을 암호화·해시한 뒤 보존기간이 지정된 감사 레코드로 저장합니다. */
public class ChatModerationAuditService {

	private final ChatModerationAuditCipher cipher;
	private final ChatModerationContentHasher contentHasher;
	private final ChatModerationAuditPersistence persistence;
	private final Duration retention;

	public ChatModerationAuditService(
		ChatModerationAuditCipher cipher,
		ChatModerationContentHasher contentHasher,
		ChatModerationAuditPersistence persistence,
		Duration retention
	) {
		this.cipher = Objects.requireNonNull(cipher, "cipher");
		this.contentHasher = Objects.requireNonNull(
			contentHasher,
			"contentHasher"
		);
		this.persistence = Objects.requireNonNull(persistence, "persistence");
		this.retention = Objects.requireNonNull(retention, "retention");
		if (retention.isZero() || retention.isNegative()) {
			throw new IllegalArgumentException("retention must be positive");
		}
	}

	public void record(ChatModerationAuditRequestedEvent event) {
		Objects.requireNonNull(event, "event");
		persistence.saveIfAbsent(new ChatModerationAuditEntry(
			event.context(),
			cipher.encrypt(event.context(), event.originalContent()),
			contentHasher.hash(event.originalContent()),
			event.maskedFields(),
			event.createdAt(),
			event.createdAt().plus(retention)
		));
	}

	public int deleteExpired(Instant now) {
		return persistence.deleteExpired(Objects.requireNonNull(now, "now"));
	}
}
