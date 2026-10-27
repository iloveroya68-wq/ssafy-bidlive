package com.ssafy.bidlive.chat.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** 새로 뮤트된 사용자에게 경매와 만료 시각을 전달합니다. */
public record ChatMutedEvent(
	long memberId,
	long auctionId,
	Duration duration,
	Instant mutedUntil
) {

	public ChatMutedEvent {
		if (memberId <= 0 || auctionId <= 0) {
			throw new IllegalArgumentException("identifiers must be positive");
		}
		Objects.requireNonNull(duration, "duration");
		Objects.requireNonNull(mutedUntil, "mutedUntil");
	}
}
