package com.ssafy.bidlive.chat.api.dto;

/** STOMP 채팅 전송 본문과 클라이언트 멱등 키를 전달한다. */
public record ChatSendRequest(
	String clientMessageId,
	String content,
	boolean isQuestion
) {
}
