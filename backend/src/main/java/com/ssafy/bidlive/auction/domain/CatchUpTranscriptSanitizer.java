package com.ssafy.bidlive.auction.domain;

import java.util.regex.Pattern;

/** AI 입력 전에 연락처·이메일·긴 숫자열을 제거하는 최소 개인정보 방어선이다. */
public final class CatchUpTranscriptSanitizer {

	private static final Pattern EMAIL = Pattern.compile(
		"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"
	);
	private static final Pattern PHONE = Pattern.compile(
		"(?<!\\d)(?:01[016789])[-. ]?\\d{3,4}[-. ]?\\d{4}(?!\\d)"
	);
	private static final Pattern LONG_NUMBER = Pattern.compile("(?<!\\d)\\d{8,}(?!\\d)");

	private CatchUpTranscriptSanitizer() {
	}

	public static String sanitize(String text) {
		if (text == null || text.isBlank()) {
			return "";
		}
		String sanitized = EMAIL.matcher(text).replaceAll("[이메일 숨김]");
		sanitized = PHONE.matcher(sanitized).replaceAll("[연락처 숨김]");
		return LONG_NUMBER.matcher(sanitized).replaceAll("[번호 숨김]").trim();
	}
}
