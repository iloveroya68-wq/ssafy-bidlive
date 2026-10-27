package com.ssafy.bidlive.chat.api.dto;

/** 채팅 이력 조회 실패의 안정적인 코드와 사용자 메시지를 반환한다. */
public record ChatHistoryErrorResponse(
	String code,
	String message
) {
}
