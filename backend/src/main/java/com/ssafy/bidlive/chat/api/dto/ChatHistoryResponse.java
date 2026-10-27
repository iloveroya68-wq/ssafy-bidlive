package com.ssafy.bidlive.chat.api.dto;

import java.util.List;

import com.ssafy.bidlive.chat.application.ChatHistoryPage;

/** 오래된 순으로 정렬된 채팅 이력과 이전 페이지 존재 여부를 반환한다. */
public record ChatHistoryResponse(
	List<ChatHistoryMessageResponse> messages,
	boolean hasMore
) {

	public static ChatHistoryResponse from(ChatHistoryPage page) {
		return new ChatHistoryResponse(
			page.messages().stream()
				.map(ChatHistoryMessageResponse::from)
				.toList(),
			page.hasMore()
		);
	}
}
