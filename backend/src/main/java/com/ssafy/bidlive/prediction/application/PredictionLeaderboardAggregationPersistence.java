package com.ssafy.bidlive.prediction.application;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/** 날짜별 중복 실행을 잠그고 일일 예측 리더보드 스냅샷을 교체하는 Port다. */
public interface PredictionLeaderboardAggregationPersistence {

	PredictionLeaderboardSnapshot replaceSnapshot(
		LocalDate rankingDate,
		OffsetDateTime rangeStart,
		OffsetDateTime rangeEnd,
		OffsetDateTime calculatedAt
	);
}
