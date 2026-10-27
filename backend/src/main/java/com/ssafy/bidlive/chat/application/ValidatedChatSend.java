package com.ssafy.bidlive.chat.application;

import java.util.Objects;

import com.ssafy.bidlive.chat.domain.ChatMessageContent;
import com.ssafy.bidlive.chat.domain.ClientMessageId;

/** 형식 검증과 정규화를 통과한 채팅 전송 입력을 전달한다. */
public record ValidatedChatSend(
	long auctionId,
	long memberId,
	ClientMessageId clientMessageId,
	ChatMessageContent content,
	boolean isQuestion
) {

	public ValidatedChatSend {
		Objects.requireNonNull(clientMessageId, "clientMessageId");
		Objects.requireNonNull(content, "content");
		if (auctionId <= 0) {
			throw new IllegalArgumentException("auctionId must be positive");
		}
		if (memberId <= 0) {
			throw new IllegalArgumentException("memberId must be positive");
		}
	}
}
