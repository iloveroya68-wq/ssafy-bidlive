package com.ssafy.bidlive.chat.domain;

import java.util.Objects;

/** 커밋 후 경매 채팅 구독자에게 메시지 제거 사실을 전달한다. */
public record ChatDeletedEvent(
	long auctionId,
	ChatMessageId messageId
) {

	public ChatDeletedEvent {
		if (auctionId <= 0) {
			throw new IllegalArgumentException("auctionId must be positive");
		}
		Objects.requireNonNull(messageId, "messageId");
	}
}
