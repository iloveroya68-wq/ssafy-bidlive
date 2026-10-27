package com.ssafy.bidlive.chat.application;

/** 채팅 이력 조회 실패를 외부에 노출할 안정적인 코드로 구분한다. */
public enum ChatHistoryErrorCode {
	UNAUTHORIZED,
	INVALID_AUCTION_ID,
	INVALID_CURSOR,
	INVALID_PAGE_SIZE,
	AUCTION_NOT_FOUND,
	ROOM_ACCESS_DENIED
}
