package com.ssafy.bidlive.chat.domain;

import java.util.Objects;

import com.ssafy.bidlive.chat.application.ChatModerationReason;

/** 검열 대상 사용자에게 공개 가능한 경고 사유만 전달합니다. */
public record ModerationWarningEvent(
	long memberId,
	ChatMessageId messageId,
	ChatModerationReason reason
) {

	public ModerationWarningEvent {
		if (memberId <= 0) {
			throw new IllegalArgumentException("memberId must be positive");
		}
		Objects.requireNonNull(messageId, "messageId");
		Objects.requireNonNull(reason, "reason");
	}
}
