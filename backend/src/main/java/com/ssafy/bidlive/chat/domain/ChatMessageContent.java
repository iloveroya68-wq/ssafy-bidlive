package com.ssafy.bidlive.chat.domain;

import java.util.Objects;

/** 채팅 본문을 정규화하고 Unicode 코드 포인트 기준 250자 제한을 보장한다. */
public record ChatMessageContent(String value) {

	public static final int MAX_LENGTH = 250;

	public ChatMessageContent {
		Objects.requireNonNull(value, "value");

		String normalized = value.strip();
		int length = normalized.codePointCount(0, normalized.length());
		if (length == 0 || length > MAX_LENGTH) {
			throw new IllegalArgumentException(
				"content length must be between 1 and " + MAX_LENGTH
			);
		}
		value = normalized;
	}
}
