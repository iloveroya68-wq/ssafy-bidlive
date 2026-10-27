package com.ssafy.bidlive.chat.application;

import java.util.Objects;

import com.ssafy.bidlive.chat.domain.ChatMessageId;

/** 채팅 전송의 서버 메시지 ID와 신규 생성 여부를 전달한다. */
public record ChatSendResult(
	ChatMessageId messageId,
	boolean created
) {

	public ChatSendResult {
		Objects.requireNonNull(messageId, "messageId");
	}

	public static ChatSendResult created(ChatMessageId messageId) {
		return new ChatSendResult(messageId, true);
	}

	public static ChatSendResult existing(ChatMessageId messageId) {
		return new ChatSendResult(messageId, false);
	}
}
