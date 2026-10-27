package com.ssafy.bidlive.chat.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 채팅 WebSocket 오류 코드가 고정 코드와 안전한 공개 문구를 제공하는지 검증한다. */
class ChatSendErrorCodeTest {

	@Test
	void exposesStableCodeAndPublicMessage() {
		assertThat(ChatSendErrorCode.AUCTION_CHAT_CLOSED.code())
			.isEqualTo("AUCTION_CHAT_CLOSED");
		assertThat(ChatSendErrorCode.CHAT_MUTED.publicMessage())
			.isEqualTo("일시적으로 채팅이 제한되었습니다.");
		assertThat(ChatSendErrorCode.SLOW_MODE_LIMIT.publicMessage())
			.doesNotContain("Redis");
		assertThat(ChatSendErrorCode.CHAT_CONTENT_INVALID.publicMessage())
			.doesNotContain("content");
	}
}
