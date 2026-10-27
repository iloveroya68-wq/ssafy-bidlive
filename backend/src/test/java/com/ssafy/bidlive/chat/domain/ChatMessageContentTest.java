package com.ssafy.bidlive.chat.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** 채팅 본문의 공백 정규화와 Unicode 250자 제한을 검증한다. */
class ChatMessageContentTest {

	@Test
	void stripsLeadingAndTrailingWhitespace() {
		ChatMessageContent content =
			new ChatMessageContent("  상품 상태가 궁금합니다.  ");

		assertThat(content.value()).isEqualTo("상품 상태가 궁금합니다.");
	}

	@Test
	void acceptsExactlyTwoHundredFiftyUnicodeCodePoints() {
		String contentWithEmoji = "가".repeat(249) + "😀";

		ChatMessageContent content =
			new ChatMessageContent(contentWithEmoji);

		assertThat(content.value().codePointCount(0, content.value().length()))
			.isEqualTo(ChatMessageContent.MAX_LENGTH);
	}

	@Test
	void rejectsMoreThanTwoHundredFiftyUnicodeCodePoints() {
		String tooLong = "가".repeat(250) + "😀";

		assertThatThrownBy(() -> new ChatMessageContent(tooLong))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsBlankContent() {
		assertThatThrownBy(() -> new ChatMessageContent(" \t\n "))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsNullContent() {
		assertThatThrownBy(() -> new ChatMessageContent(null))
			.isInstanceOf(NullPointerException.class);
	}
}
