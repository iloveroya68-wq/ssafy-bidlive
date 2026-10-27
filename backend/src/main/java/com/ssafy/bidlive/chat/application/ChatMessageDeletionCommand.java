package com.ssafy.bidlive.chat.application;

import java.time.Instant;
import java.util.Objects;

import com.ssafy.bidlive.chat.domain.ChatMessageDeletionReason;
import com.ssafy.bidlive.chat.domain.ChatMessageId;

/** 소프트 삭제 저장에 필요한 대상·수행자·감사 정보를 전달한다. */
public record ChatMessageDeletionCommand(
	long auctionId,
	ChatMessageId messageId,
	Long deletedByMemberId,
	ChatMessageDeletionReason reason,
	Instant deletedAt
) {

	public ChatMessageDeletionCommand {
		if (auctionId <= 0) {
			throw new IllegalArgumentException("auctionId must be positive");
		}
		Objects.requireNonNull(messageId, "messageId");
		Objects.requireNonNull(reason, "reason");
		Objects.requireNonNull(deletedAt, "deletedAt");
		if (deletedByMemberId != null && deletedByMemberId <= 0) {
			throw new IllegalArgumentException(
				"deletedByMemberId must be positive"
			);
		}
		if (isSystemDeletion(reason) && deletedByMemberId != null) {
			throw new IllegalArgumentException(
				"system deletion must not have member actor"
			);
		}
		if (!isSystemDeletion(reason)
			&& deletedByMemberId == null) {
			throw new IllegalArgumentException(
				"member deletion must have actor"
			);
		}
	}

	private static boolean isSystemDeletion(ChatMessageDeletionReason reason) {
		return reason == ChatMessageDeletionReason.MODERATION_BLOCKED
			|| reason == ChatMessageDeletionReason.QUESTION_FILTERED;
	}
}
