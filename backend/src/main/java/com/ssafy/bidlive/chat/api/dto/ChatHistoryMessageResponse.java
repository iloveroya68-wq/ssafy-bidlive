package com.ssafy.bidlive.chat.api.dto;

import java.time.Instant;
import java.util.List;

import com.ssafy.bidlive.chat.domain.ChatMessage;
import com.ssafy.bidlive.chat.domain.MaskingCategory;

/** WebSocket CHAT_MESSAGE와 동일한 채팅 메시지 필드를 REST 이력 응답으로 제공한다. */
public record ChatHistoryMessageResponse(
	String messageId,
	String clientMessageId,
	long memberId,
	String nickname,
	String content,
	boolean isQuestion,
	List<MaskingCategory> maskedFields,
	Instant createdAt
) {

	public static ChatHistoryMessageResponse from(ChatMessage message) {
		return new ChatHistoryMessageResponse(
			message.messageId().value(),
			message.clientMessageId().value(),
			message.memberId(),
			message.nickname(),
			message.content().value(),
			message.isQuestion(),
			message.maskedFields().stream().sorted().toList(),
			message.createdAt()
		);
	}
}
