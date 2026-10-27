package com.ssafy.bidlive.chat.domain;

/** 질문 큐 조회·해결 요청이 실패한 안정적인 사유를 정의한다. */
public enum QuestionQueueErrorCode {
	UNAUTHORIZED,
	INVALID_AUCTION_ID,
	NOT_AUCTION_HOST,
	INVALID_CURSOR,
	INVALID_PAGE_SIZE,
	QUESTION_NOT_FOUND
}
