package com.ssafy.bidlive.common.application;

import java.time.Instant;
import java.util.Objects;

/** 한 Redis 시점에서 읽은 경매 공개 상태와 고유 입찰자 수를 표현합니다. */
public record RealtimeAuctionStateSnapshot(
	long stateVersion,
	Status status,
	Instant endAt,
	int extendCount,
	long bidCount,
	long bidParticipantCount
) {

	public RealtimeAuctionStateSnapshot {
		requireNonNegative(stateVersion, "stateVersion");
		Objects.requireNonNull(status, "status");
		Objects.requireNonNull(endAt, "endAt");
		requireNonNegative(extendCount, "extendCount");
		requireNonNegative(bidCount, "bidCount");
		requireNonNegative(bidParticipantCount, "bidParticipantCount");
	}

	private static void requireNonNegative(long value, String fieldName) {
		if (value < 0) {
			throw new IllegalArgumentException(fieldName + " must not be negative");
		}
	}

	/** Redis 경매 상태 Hash에서 허용하는 공개 진행 상태입니다. */
	public enum Status {
		WAITING,
		LIVE,
		ENDED
	}
}
