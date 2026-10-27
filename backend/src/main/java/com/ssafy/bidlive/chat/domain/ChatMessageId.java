package com.ssafy.bidlive.chat.domain;

import java.util.Objects;
import java.util.regex.Pattern;

/** 서버가 발급한 26자리 ULID 채팅 메시지 식별자를 표현한다. */
public record ChatMessageId(String value) {

	private static final Pattern ULID_PATTERN =
		Pattern.compile("[0-7][0-9A-HJKMNP-TV-Z]{25}");

	public ChatMessageId {
		Objects.requireNonNull(value, "value");
		if (!ULID_PATTERN.matcher(value).matches()) {
			throw new IllegalArgumentException("messageId must be a canonical ULID");
		}
	}
}
