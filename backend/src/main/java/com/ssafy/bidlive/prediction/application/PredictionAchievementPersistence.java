package com.ssafy.bidlive.prediction.application;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/** 회원별 예측 성과 원본 집계와 저장된 등급·뱃지를 조회하는 영속성 계약이다. */
public interface PredictionAchievementPersistence {

	Statistics findStatistics(Long memberId);

	Optional<Grade> findGrade(Long memberId);

	List<Badge> findBadges(Long memberId);

	record Statistics(
		long predictionCount,
		long hitCount,
		long totalStakedPoints,
		long totalRewardPoints
	) {
	}

	record Grade(
		String code,
		long score
	) {
	}

	record Badge(
		String code,
		OffsetDateTime earnedAt
	) {
	}
}
