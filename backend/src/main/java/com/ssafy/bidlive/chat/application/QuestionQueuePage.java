package com.ssafy.bidlive.chat.application;

import java.util.List;

import com.ssafy.bidlive.chat.domain.QuestionQueueItem;

/** PENDING 질문 페이지와 다음 조회 커서를 반환한다. */
public record QuestionQueuePage(
	List<QuestionQueueItem> items,
	String nextCursor,
	boolean hasNext
) {

	public QuestionQueuePage {
		items = List.copyOf(items);
	}
}
