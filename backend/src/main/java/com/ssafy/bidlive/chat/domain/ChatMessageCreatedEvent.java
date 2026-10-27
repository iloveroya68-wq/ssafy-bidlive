package com.ssafy.bidlive.chat.domain;

import java.util.Objects;

/** 트랜잭션에 신규 저장된 채팅 메시지를 후속 처리에 전달한다. */
public record ChatMessageCreatedEvent(ChatMessage message) {

	public ChatMessageCreatedEvent {
		Objects.requireNonNull(message, "message");
	}
}
