package com.ssafy.bidlive.prediction.application;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;

/** 확정된 날짜별 읽기 모델에서 페이지와 회원 본인 순위를 조회하는 Port다. */
public interface PredictionLeaderboardQueryPersistence {

	PredictionLeaderboardPage findPage(
		LocalDate rankingDate,
		PredictionLeaderboardCursor cursor,
		int size
	);

	PredictionLeaderboardTop10Snapshot findTop10(
		LocalDate rankingDate,
		OffsetDateTime calculatedAt
	);

	Optional<PredictionLeaderboardEntry> findMyRank(
		LocalDate rankingDate,
		OffsetDateTime calculatedAt,
		long memberId
	);
}
