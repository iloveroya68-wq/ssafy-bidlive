package com.ssafy.bidlive.prediction.application;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/** DB 커밋 후 해당 날짜의 상위 10명 Redis 캐시를 갱신하기 위한 이벤트다. */
public record PredictionLeaderboardAggregatedEvent(
	LocalDate rankingDate,
	OffsetDateTime calculatedAt
) {

	public PredictionLeaderboardAggregatedEvent {
		if (rankingDate == null || calculatedAt == null) {
			throw new IllegalArgumentException(
				"rankingDate and calculatedAt must not be null"
			);
		}
	}
}
