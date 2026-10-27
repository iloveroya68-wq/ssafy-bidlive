package com.ssafy.bidlive.chat.application;

import java.util.Optional;

import com.ssafy.bidlive.chat.domain.ChatMessage;
import com.ssafy.bidlive.chat.domain.ChatMessageId;
import com.ssafy.bidlive.chat.domain.ClientMessageId;

/** 채팅의 발신자별 멱등 결과 조회와 충돌 없는 신규 저장을 제공한다. */
public interface ChatMessagePersistence {

	Optional<ChatMessageId> findMessageId(
		long memberId,
		ClientMessageId clientMessageId
	);

	ChatMessagePersistenceResult saveIfAbsent(ChatMessage message);
}
