package com.ssafy.bidlive.chat.domain;

import java.util.Objects;

/** 경매 채팅에서 현재 고정된 공지 메시지의 공개 정보를 표현합니다. */
public record PinnedNotice(
	String messageId,
	String content
) {

	public PinnedNotice {
		if (messageId == null || messageId.isBlank()) {
			throw new IllegalArgumentException("messageId must not be blank");
		}
		Objects.requireNonNull(content, "content");
	}
}
