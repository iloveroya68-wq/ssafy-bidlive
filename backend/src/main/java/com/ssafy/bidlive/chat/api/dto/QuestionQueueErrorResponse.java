package com.ssafy.bidlive.chat.api.dto;

/** 질문 큐 API 실패의 안정적인 코드와 사용자 메시지를 반환한다. */
public record QuestionQueueErrorResponse(
	String code,
	String message
) {
}
