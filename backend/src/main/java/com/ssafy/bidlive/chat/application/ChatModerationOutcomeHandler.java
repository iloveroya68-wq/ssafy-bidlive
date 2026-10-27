package com.ssafy.bidlive.chat.application;

/** 확정된 LLM 판정을 채팅 상태와 후속 이벤트에 원자적으로 반영합니다. */
public interface ChatModerationOutcomeHandler {

	void apply(ChatModerationJob job, ChatModerationJudgement judgement);
}
