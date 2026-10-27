package com.ssafy.bidlive.chat.application;

/** 질문 해결 시 신규 상태 전이·기처리·미존재 결과를 구분한다. */
public enum QuestionResolutionResult {
	RESOLVED,
	ALREADY_HANDLED,
	NOT_FOUND
}
