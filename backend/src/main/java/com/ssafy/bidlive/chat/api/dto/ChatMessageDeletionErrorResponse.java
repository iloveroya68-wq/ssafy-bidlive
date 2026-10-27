package com.ssafy.bidlive.chat.api.dto;

/** 채팅 삭제 API가 안정적인 오류 코드와 공개 메시지를 반환합니다. */
public record ChatMessageDeletionErrorResponse(
	String code,
	String message
) {
}
