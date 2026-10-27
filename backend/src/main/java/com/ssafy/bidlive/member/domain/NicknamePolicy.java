package com.ssafy.bidlive.member.domain;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** 서비스 닉네임의 정규화, 형식 및 금칙어 포함 여부를 판정한다. */
public final class NicknamePolicy {

	public static final int MAX_LENGTH = 30;

	private static final Pattern ALLOWED_CHARACTERS =
		Pattern.compile("^[가-힣A-Za-z0-9]+$");

	private NicknamePolicy() {
	}

	public static String normalize(String nickname) {
		return nickname == null ? null : nickname.strip();
	}

	public static String normalizedKey(String nickname) {
		String normalizedNickname = normalize(nickname);
		return normalizedNickname == null
			? null
			: normalizedNickname.toLowerCase(Locale.ROOT);
	}

	public static boolean isValidFormat(String nickname) {
		if (nickname == null || nickname.isBlank()) {
			return false;
		}

		int length = nickname.codePointCount(0, nickname.length());
		return length <= MAX_LENGTH && ALLOWED_CHARACTERS.matcher(nickname).matches();
	}

	public static boolean containsProhibitedWord(
		String nickname,
		Set<String> prohibitedWords
	) {
		Objects.requireNonNull(nickname, "nickname");
		Objects.requireNonNull(prohibitedWords, "prohibitedWords");

		String comparableNickname = nickname.toLowerCase(Locale.ROOT);
		return prohibitedWords.stream()
			.filter(Objects::nonNull)
			.map(String::strip)
			.filter(word -> !word.isBlank())
			.map(word -> word.toLowerCase(Locale.ROOT))
			.anyMatch(comparableNickname::contains);
	}
}
