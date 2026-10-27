package com.ssafy.bidlive.chat.domain;

/** 고정 공지 등록·해제 요청이 실패한 안정적인 사유를 정의합니다. */
public enum PinnedNoticeErrorCode {
	UNAUTHORIZED,
	INVALID_AUCTION_ID,
	INVALID_MESSAGE_ID,
	AUCTION_NOT_FOUND,
	NOT_AUCTION_HOST,
	MESSAGE_NOT_FOUND
}
