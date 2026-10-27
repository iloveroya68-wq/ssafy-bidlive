package com.ssafy.bidlive.chat.application;

/** 인증 사용자의 채팅 전송 입력을 Application 계층으로 전달한다. */
public record ChatSendCommand(
	long auctionId,
	long memberId,
	String clientMessageId,
	String content,
	boolean isQuestion
) {
}
