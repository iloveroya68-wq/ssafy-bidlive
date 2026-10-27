package com.ssafy.bidlive.prediction.application;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** 특정 서울 날짜의 집계 시각과 전체 예측 순위를 보존하는 불변 스냅샷이다. */
public record PredictionLeaderboardSnapshot(
	LocalDate rankingDate,
	OffsetDateTime calculatedAt,
	List<PredictionLeaderboardEntry> entries
) {

	public PredictionLeaderboardSnapshot {
		entries = List.copyOf(entries);
	}
}
