package com.ssafy.bidlive.prediction.application;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** 날짜별 리더보드의 한 페이지와 다음 조회 위치를 표현한다. */
public record PredictionLeaderboardPage(
	LocalDate rankingDate,
	OffsetDateTime calculatedAt,
	List<PredictionLeaderboardEntry> items,
	String nextCursor,
	boolean hasNext
) {

	public PredictionLeaderboardPage {
		if (rankingDate == null || items == null) {
			throw new IllegalArgumentException(
				"rankingDate and items must not be null"
			);
		}
		items = List.copyOf(items);
		if (calculatedAt == null && !items.isEmpty()) {
			throw new IllegalArgumentException(
				"calculatedAt is required when items exist"
			);
		}
		if (hasNext && (items.isEmpty()
			|| nextCursor == null
			|| nextCursor.isBlank())) {
			throw new IllegalArgumentException(
				"nextCursor is required when hasNext is true"
			);
		}
		if (!hasNext && nextCursor != null) {
			throw new IllegalArgumentException(
				"nextCursor must be null when hasNext is false"
			);
		}
	}
}
