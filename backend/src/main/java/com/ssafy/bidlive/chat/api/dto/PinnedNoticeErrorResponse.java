package com.ssafy.bidlive.chat.api.dto;

/** 고정 공지 API 실패의 안정적인 코드와 사용자 메시지를 반환합니다. */
public record PinnedNoticeErrorResponse(
	String code,
	String message
) {
}
