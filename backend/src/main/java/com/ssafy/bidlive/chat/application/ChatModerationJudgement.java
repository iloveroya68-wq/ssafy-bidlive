package com.ssafy.bidlive.chat.application;

import java.util.Objects;

/** 채팅 LLM의 정규화된 판정과 공개 사유를 전달합니다. */
public record ChatModerationJudgement(
	ChatModerationDecision decision,
	ChatModerationReason reason
) {

	public ChatModerationJudgement {
		Objects.requireNonNull(decision, "decision");
		Objects.requireNonNull(reason, "reason");
		if (decision == ChatModerationDecision.ALLOW
			&& reason != ChatModerationReason.SAFE) {
			throw new IllegalArgumentException("ALLOW reason must be SAFE");
		}
	}
}
