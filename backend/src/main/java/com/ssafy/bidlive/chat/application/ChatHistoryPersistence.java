package com.ssafy.bidlive.chat.application;

import java.util.Optional;

import com.ssafy.bidlive.chat.domain.ChatMessageId;

/** 공개 ULID 커서 이전의 삭제되지 않은 채팅 이력을 조회한다. */
public interface ChatHistoryPersistence {

	Optional<ChatHistoryPage> findBefore(
		long auctionId,
		ChatMessageId before,
		int size
	);
}
