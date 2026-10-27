package com.ssafy.bidlive.chat.domain;

import java.util.Objects;

/** 커밋 후 판매자 질문 토픽에 제거 사실을 전달할 도메인 이벤트다. */
public record QuestionRemovedEvent(
	long auctionId,
	ChatMessageId questionId,
	QuestionRemovalReason reason
) {

	public QuestionRemovedEvent {
		if (auctionId <= 0) {
			throw new IllegalArgumentException("auctionId must be positive");
		}
		Objects.requireNonNull(questionId, "questionId");
		Objects.requireNonNull(reason, "reason");
	}
}
