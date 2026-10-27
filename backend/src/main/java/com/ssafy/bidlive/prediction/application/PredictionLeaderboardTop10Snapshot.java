package com.ssafy.bidlive.prediction.application;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** 날짜별 기본 첫 페이지로 캐시하는 최대 10명의 리더보드 결과다. */
public record PredictionLeaderboardTop10Snapshot(
	LocalDate rankingDate,
	OffsetDateTime calculatedAt,
	List<PredictionLeaderboardEntry> items,
	String nextCursor,
	boolean hasNext
) {

	private static final int MAX_ITEM_COUNT = 10;

	public PredictionLeaderboardTop10Snapshot {
		if (rankingDate == null || calculatedAt == null || items == null) {
			throw new IllegalArgumentException(
				"rankingDate, calculatedAt and items must not be null"
			);
		}
		items = List.copyOf(items);
		if (items.size() > MAX_ITEM_COUNT) {
			throw new IllegalArgumentException(
				"top10 snapshot must not contain more than 10 items"
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
