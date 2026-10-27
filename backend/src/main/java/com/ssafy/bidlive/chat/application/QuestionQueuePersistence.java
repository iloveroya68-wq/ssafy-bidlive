package com.ssafy.bidlive.chat.application;

import java.util.List;
import java.util.Optional;

import com.ssafy.bidlive.chat.domain.ChatMessageId;
import com.ssafy.bidlive.chat.domain.QuestionQueueItem;

/** 질문 커서 복원과 PENDING 질문의 안정 정렬 조회를 제공한다. */
public interface QuestionQueuePersistence {

	Optional<QuestionQueueCursor> findCursor(
		long auctionId,
		ChatMessageId questionId
	);

	List<QuestionQueueItem> findPendingAfter(
		long auctionId,
		Optional<QuestionQueueCursor> cursor,
		int limit
	);

	QuestionResolutionResult resolvePending(
		long auctionId,
		ChatMessageId questionId
	);
}
