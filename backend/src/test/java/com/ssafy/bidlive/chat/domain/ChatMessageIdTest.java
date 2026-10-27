package com.ssafy.bidlive.chat.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** 서버 메시지 ID가 128비트 범위의 정규 ULID인지 검증한다. */
class ChatMessageIdTest {

	@Test
	void acceptsCanonicalUlid() {
		String ulid = "01J8W3P2N9KQ4T123456789ABC";

		assertThat(new ChatMessageId(ulid).value()).isEqualTo(ulid);
	}

	@Test
	void rejectsUlidOutsideOneHundredTwentyEightBitRange() {
		assertThatThrownBy(() ->
			new ChatMessageId("81J8W3P2N9KQ4T123456789ABC")
		).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsAmbiguousCrockfordCharacter() {
		assertThatThrownBy(() ->
			new ChatMessageId("01J8W3P2N9KQ4T123456789ABU")
		).isInstanceOf(IllegalArgumentException.class);
	}
}
