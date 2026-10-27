package com.ssafy.bidlive.prediction.application;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** 한 페이지의 일일 리더보드와 로그인 회원의 전체 순위를 결합한 결과다. */
public record PredictionLeaderboardQueryResult(
	LocalDate rankingDate,
	OffsetDateTime calculatedAt,
	List<PredictionLeaderboardEntry> items,
	PredictionLeaderboardEntry myRank,
	String nextCursor,
	boolean hasNext
) {

	public PredictionLeaderboardQueryResult {
		if (rankingDate == null || items == null) {
			throw new IllegalArgumentException(
				"rankingDate and items must not be null"
			);
		}
		items = List.copyOf(items);
		if (calculatedAt == null && (!items.isEmpty()
			|| myRank != null
			|| nextCursor != null
			|| hasNext)) {
			throw new IllegalArgumentException(
				"empty leaderboard must not contain snapshot data"
			);
		}
	}
}
