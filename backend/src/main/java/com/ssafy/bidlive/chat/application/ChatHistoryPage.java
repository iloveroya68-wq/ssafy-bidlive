package com.ssafy.bidlive.chat.application;

import java.util.List;
import java.util.Objects;

import com.ssafy.bidlive.chat.domain.ChatMessage;

/** 오래된 순으로 정렬된 채팅 이력 한 페이지와 다음 페이지 존재 여부를 표현한다. */
public record ChatHistoryPage(
	List<ChatMessage> messages,
	boolean hasMore
) {

	public ChatHistoryPage {
		Objects.requireNonNull(messages, "messages");
		messages = List.copyOf(messages);
	}
}
