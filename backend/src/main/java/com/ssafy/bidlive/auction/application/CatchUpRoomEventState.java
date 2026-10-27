package com.ssafy.bidlive.auction.application;

import java.time.Instant;

/** Redis에 누적한 공개 가능한 RoomEvent의 최신 집계다. */
public record CatchUpRoomEventState(
	long bidCount,
	long participantCount,
	int extensionCount,
	int extendedMinutes,
	Instant startedAt,
	Instant endAt
) {
	public CatchUpRoomEventState {
		if (bidCount < 0 || participantCount < 0) {
			throw new IllegalArgumentException("room event counts must not be negative");
		}
		if (extensionCount < 0 || extendedMinutes < 0) {
			throw new IllegalArgumentException("extension values must not be negative");
		}
	}
}
