package com.ssafy.bidlive.chat.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/** 검증과 마스킹을 마친 뒤 저장·송출할 채팅 메시지를 표현한다. */
public record ChatMessage(
	ChatMessageId messageId,
	ClientMessageId clientMessageId,
	long auctionId,
	long memberId,
	String nickname,
	ChatMessageContent content,
	boolean isQuestion,
	Set<MaskingCategory> maskedFields,
	Instant createdAt
) {

	public ChatMessage {
		Objects.requireNonNull(messageId, "messageId");
		Objects.requireNonNull(clientMessageId, "clientMessageId");
		Objects.requireNonNull(nickname, "nickname");
		Objects.requireNonNull(content, "content");
		Objects.requireNonNull(maskedFields, "maskedFields");
		Objects.requireNonNull(createdAt, "createdAt");

		if (auctionId <= 0) {
			throw new IllegalArgumentException("auctionId must be positive");
		}
		if (memberId <= 0) {
			throw new IllegalArgumentException("memberId must be positive");
		}

		String normalizedNickname = nickname.strip();
		if (normalizedNickname.isEmpty()) {
			throw new IllegalArgumentException("nickname must not be blank");
		}
		nickname = normalizedNickname;
		maskedFields = Set.copyOf(maskedFields);
	}
}
