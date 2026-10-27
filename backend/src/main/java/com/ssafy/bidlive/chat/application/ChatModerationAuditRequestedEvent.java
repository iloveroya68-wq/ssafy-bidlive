package com.ssafy.bidlive.chat.application;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

import com.ssafy.bidlive.chat.domain.MaskingCategory;

/** 마스킹된 신규 채팅의 원문을 커밋 이후 감사 기록하도록 요청합니다. */
public final class ChatModerationAuditRequestedEvent {

	private final ChatModerationAuditContext context;
	private final String originalContent;
	private final Set<MaskingCategory> maskedFields;
	private final Instant createdAt;

	public ChatModerationAuditRequestedEvent(
		ChatModerationAuditContext context,
		String originalContent,
		Set<MaskingCategory> maskedFields,
		Instant createdAt
	) {
		this.context = Objects.requireNonNull(context, "context");
		if (originalContent == null || originalContent.isEmpty()) {
			throw new IllegalArgumentException(
				"originalContent must not be empty"
			);
		}
		this.originalContent = originalContent;
		this.maskedFields = Set.copyOf(
			Objects.requireNonNull(maskedFields, "maskedFields")
		);
		if (this.maskedFields.isEmpty()) {
			throw new IllegalArgumentException("maskedFields must not be empty");
		}
		this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
	}

	public ChatModerationAuditContext context() {
		return context;
	}

	public String originalContent() {
		return originalContent;
	}

	public Set<MaskingCategory> maskedFields() {
		return maskedFields;
	}

	public Instant createdAt() {
		return createdAt;
	}

	@Override
	public String toString() {
		return "ChatModerationAuditRequestedEvent[messageId="
			+ context.messageId().value()
			+ ", maskedFields=" + maskedFields
			+ ", createdAt=" + createdAt + "]";
	}
}
