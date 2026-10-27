package com.ssafy.bidlive.chat.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** 동일 원문 해시의 결정성과 평문 비노출 형식을 검증합니다. */
class Sha256ChatModerationContentHasherTest {

	private final Sha256ChatModerationContentHasher hasher =
		new Sha256ChatModerationContentHasher();

	@Test
	void returnsDeterministicLowercaseSha256Hex() {
		String first = hasher.hash("카톡 abc123");
		String second = hasher.hash("카톡 abc123");

		assertThat(first)
			.isEqualTo(second)
			.matches("[0-9a-f]{64}")
			.doesNotContain("abc123");
	}

	@Test
	void rejectsNullContent() {
		assertThatThrownBy(() -> hasher.hash(null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("originalContent must not be null");
	}
}
