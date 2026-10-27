package com.ssafy.bidlive.chat.domain;

/** 저장할 채팅 메시지의 서버 권위 식별자를 발급한다. */
public interface ChatMessageIdGenerator {

	ChatMessageId nextId();
}
