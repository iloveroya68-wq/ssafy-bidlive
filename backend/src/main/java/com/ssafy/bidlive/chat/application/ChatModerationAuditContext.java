package com.ssafy.bidlive.chat.application;

import java.util.Objects;

import com.ssafy.bidlive.chat.domain.ChatMessageId;

/** 검열 원문 암호문을 특정 채팅과 요청자 문맥에 결합합니다. */
public record ChatModerationAuditContext(
	ChatMessageId messageId,
	long auctionId,
	long memberId
) {

	public ChatModerationAuditContext {
		Objects.requireNonNull(messageId, "messageId");
		if (auctionId <= 0) {
			throw new IllegalArgumentException("auctionId must be positive");
		}
		if (memberId <= 0) {
			throw new IllegalArgumentException("memberId must be positive");
		}
	}
}
