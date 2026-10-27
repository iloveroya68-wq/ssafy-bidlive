package com.ssafy.bidlive.prediction.infrastructure.redis;

import java.time.LocalDate;
import java.util.Objects;

/** 날짜별 일일 리더보드 Redis Key를 한 곳에서 생성한다. */
public final class PredictionLeaderboardRedisKey {

	private static final String DAILY_PREFIX =
		"prediction:leaderboard:daily:";
	private static final String TOP10_SUFFIX = ":top10";

	private PredictionLeaderboardRedisKey() {
	}

	public static String top10(LocalDate rankingDate) {
		return DAILY_PREFIX
			+ Objects.requireNonNull(rankingDate, "rankingDate")
			+ TOP10_SUFFIX;
	}
}
