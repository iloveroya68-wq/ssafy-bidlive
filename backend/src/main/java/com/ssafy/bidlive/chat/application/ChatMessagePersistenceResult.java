package com.ssafy.bidlive.chat.application;

import java.util.Objects;

import com.ssafy.bidlive.chat.domain.ChatMessageId;

/** PostgreSQL 멱등 저장의 서버 메시지 ID와 신규 생성 여부를 전달한다. */
public record ChatMessagePersistenceResult(
	ChatMessageId messageId,
	boolean created
) {

	public ChatMessagePersistenceResult {
		Objects.requireNonNull(messageId, "messageId");
	}

	public static ChatMessagePersistenceResult created(
		ChatMessageId messageId
	) {
		return new ChatMessagePersistenceResult(messageId, true);
	}

	public static ChatMessagePersistenceResult existing(
		ChatMessageId messageId
	) {
		return new ChatMessagePersistenceResult(messageId, false);
	}
}
