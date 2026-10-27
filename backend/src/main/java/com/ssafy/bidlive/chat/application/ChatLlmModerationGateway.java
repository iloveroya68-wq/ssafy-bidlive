package com.ssafy.bidlive.chat.application;

/** 마스킹된 채팅 본문을 외부 LLM 정책 판정에 전달합니다. */
public interface ChatLlmModerationGateway {

	ChatModerationJudgement moderate(ChatModerationJob job);
}
