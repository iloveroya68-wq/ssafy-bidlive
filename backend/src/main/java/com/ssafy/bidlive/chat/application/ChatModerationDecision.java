package com.ssafy.bidlive.chat.application;

/** LLM이 채팅에 적용할 수 있는 공개 정책 결과를 정의합니다. */
public enum ChatModerationDecision {
	ALLOW,
	WARNING,
	BLOCKED,
	QUESTION_FILTERED
}
