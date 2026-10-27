package com.ssafy.bidlive.chat.application;

import java.time.Instant;
import java.util.Objects;

import com.ssafy.bidlive.chat.domain.ChatMessageId;

/** 비동기 검열 큐에 저장할 마스킹 이후의 채팅 정보만 표현합니다. */
public record ChatModerationJob(
	long auctionId,
	ChatMessageId messageId,
	long memberId,
	String filteredContent,
	boolean question,
	String roomTitle,
	String productDescription,
	String productCategoryCode,
	Instant createdAt
) {

	/**
	 * Keeps jobs created before room context was added readable during a rolling
	 * deployment. New question jobs are created with the context-aware
	 * constructor below.
	 */
	public ChatModerationJob(
		long auctionId,
		ChatMessageId messageId,
		long memberId,
		String filteredContent,
		boolean question,
		Instant createdAt
	) {
		this(
			auctionId,
			messageId,
			memberId,
			filteredContent,
			question,
			null,
			null,
			null,
			createdAt
		);
	}

	public ChatModerationJob {
		if (auctionId <= 0 || memberId <= 0) {
			throw new IllegalArgumentException("identifiers must be positive");
		}
		Objects.requireNonNull(messageId, "messageId");
		Objects.requireNonNull(filteredContent, "filteredContent");
		Objects.requireNonNull(createdAt, "createdAt");
		filteredContent = filteredContent.strip();
		if (filteredContent.isEmpty() || filteredContent.length() > 250) {
			throw new IllegalArgumentException(
				"filteredContent length must be between 1 and 250"
			);
		}
		roomTitle = normalizeOptional(roomTitle, "roomTitle");
		productDescription = normalizeOptional(
			productDescription,
			"productDescription"
		);
		productCategoryCode = normalizeOptional(
			productCategoryCode,
			"productCategoryCode"
		);
	}

	private static String normalizeOptional(String value, String fieldName) {
		if (value == null) {
			return null;
		}
		String normalized = value.strip();
		if (normalized.isEmpty()) {
			throw new IllegalArgumentException(fieldName + " must not be blank");
		}
		return normalized;
	}
}
