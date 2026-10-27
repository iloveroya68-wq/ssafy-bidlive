package com.ssafy.bidlive.chat.application;

import java.time.Instant;
import java.util.Objects;

import com.ssafy.bidlive.chat.domain.ChatMessageId;

/** 질문 ID 커서가 가리키는 안정 정렬 기준 시각을 함께 전달한다. */
public record QuestionQueueCursor(
	ChatMessageId questionId,
	Instant createdAt
) {

	public QuestionQueueCursor {
		Objects.requireNonNull(questionId, "questionId");
		Objects.requireNonNull(createdAt, "createdAt");
	}
}
