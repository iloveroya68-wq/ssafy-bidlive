package com.ssafy.bidlive.chat.api.dto;

import java.util.List;

import com.ssafy.bidlive.chat.application.QuestionQueuePage;

/** 질문 큐 페이지의 항목·다음 커서·추가 페이지 여부를 반환한다. */
public record QuestionQueueResponse(
	List<QuestionQueueItemResponse> items,
	String nextCursor,
	boolean hasNext
) {

	public QuestionQueueResponse {
		items = List.copyOf(items);
	}

	public static QuestionQueueResponse from(QuestionQueuePage page) {
		return new QuestionQueueResponse(
			page.items().stream()
				.map(QuestionQueueItemResponse::from)
				.toList(),
			page.nextCursor(),
			page.hasNext()
		);
	}
}
