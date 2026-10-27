package com.ssafy.bidlive.chat.application;

/** BLOCKED 이외의 확정 판정을 채팅과 질문 상태에 멱등 반영합니다. */
public interface ChatModerationOutcomePersistence {

	ModerationOutcomeApplyResult apply(
		ChatModerationJob job,
		ChatModerationDecision decision
	);
}
