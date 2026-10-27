package com.ssafy.bidlive.chat.domain;

import java.util.Objects;
import java.util.regex.Pattern;

/** 클라이언트 재전송을 식별하는 UUID 또는 ULID 멱등 키를 표현한다. */
public record ClientMessageId(String value) {

	private static final Pattern UUID_PATTERN = Pattern.compile(
		"[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-"
			+ "[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"
	);
	private static final Pattern ULID_PATTERN =
		Pattern.compile("[0-7][0-9A-HJKMNP-TV-Z]{25}");

	public ClientMessageId {
		Objects.requireNonNull(value, "value");
		if (!UUID_PATTERN.matcher(value).matches()
			&& !ULID_PATTERN.matcher(value).matches()) {
			throw new IllegalArgumentException(
				"clientMessageId must be a UUID or canonical ULID"
			);
		}
	}
}
