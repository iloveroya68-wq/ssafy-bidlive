package com.ssafy.bidlive.chat.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssafy.bidlive.chat.domain.ChatMessageId;
import org.junit.jupiter.api.Test;

/** 신규 저장과 기존 멱등 결과의 이벤트 발행 구분을 검증한다. */
class ChatSendResultTest {

	private static final ChatMessageId MESSAGE_ID =
		new ChatMessageId("01J8W3P2N9KQ4T123456789ABC");

	@Test
	void marksCreatedMessageForPublication() {
		ChatSendResult result = ChatSendResult.created(MESSAGE_ID);

		assertThat(result.created()).isTrue();
		assertThat(result.messageId()).isEqualTo(MESSAGE_ID);
	}

	@Test
	void marksExistingMessageAsDuplicate() {
		ChatSendResult result = ChatSendResult.existing(MESSAGE_ID);

		assertThat(result.created()).isFalse();
		assertThat(result.messageId()).isEqualTo(MESSAGE_ID);
	}
}
