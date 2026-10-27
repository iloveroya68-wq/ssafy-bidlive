package com.ssafy.bidlive.chat.api.dto;

import java.time.Instant;

import com.ssafy.bidlive.chat.domain.QuestionQueueItem;

/** 질문 큐 항목의 질문·작성자·본문·생성 시각을 반환한다. */
public record QuestionQueueItemResponse(
	String questionId,
	long memberId,
	String nickname,
	String content,
	Instant createdAt
) {

	public static QuestionQueueItemResponse from(QuestionQueueItem item) {
		return new QuestionQueueItemResponse(
			item.questionId().value(),
			item.memberId(),
			item.nickname(),
			item.content(),
			item.createdAt()
		);
	}
}
