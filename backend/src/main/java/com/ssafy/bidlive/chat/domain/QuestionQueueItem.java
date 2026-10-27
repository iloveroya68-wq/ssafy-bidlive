package com.ssafy.bidlive.chat.domain;

import java.time.Instant;
import java.util.Objects;

/** 판매자가 복원할 미답변 질문의 식별자·작성자·본문을 표현한다. */
public record QuestionQueueItem(
	ChatMessageId questionId,
	long memberId,
	String nickname,
	String content,
	Instant createdAt
) {

	public QuestionQueueItem {
		Objects.requireNonNull(questionId, "questionId");
		Objects.requireNonNull(nickname, "nickname");
		Objects.requireNonNull(content, "content");
		Objects.requireNonNull(createdAt, "createdAt");
		if (memberId <= 0) {
			throw new IllegalArgumentException("memberId must be positive");
		}
		if (nickname.isBlank()) {
			throw new IllegalArgumentException("nickname must not be blank");
		}
		if (content.isBlank()) {
			throw new IllegalArgumentException("content must not be blank");
		}
	}
}
