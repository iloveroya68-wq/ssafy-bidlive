package com.ssafy.bidlive.chat.api.dto;

import com.ssafy.bidlive.chat.domain.PinnedNotice;

/** 고정된 공지 메시지의 식별자와 내용을 반환합니다. */
public record PinnedNoticeResponse(
	String messageId,
	String content
) {

	public static PinnedNoticeResponse from(PinnedNotice notice) {
		return new PinnedNoticeResponse(notice.messageId(), notice.content());
	}
}
