package com.ssafy.bidlive.prediction.domain;

import java.util.EnumSet;
import java.util.Set;

/** 환불을 제외한 예측 정확도 점수와 누적 성과에 따른 등급·배지 자격을 계산한다. */
public class PredictionAchievementPolicy {

	public int scoreFor(PredictionSettlementBand band) {
		if (band == null) {
			throw new IllegalArgumentException("band must not be null");
		}
		return switch (band) {
			case WITHIN_5 -> 100;
			case WITHIN_10 -> 50;
			case WITHIN_20 -> 20;
			case MISS, REFUNDED -> 0;
		};
	}

	public PredictionAchievementGrade gradeFor(long score) {
		if (score < 0) {
			throw new IllegalArgumentException("score must not be negative");
		}
		PredictionAchievementGrade matched =
			PredictionAchievementGrade.ORACLE_1;
		for (PredictionAchievementGrade grade
			: PredictionAchievementGrade.values()) {
			if (score < grade.minimumScore()) {
				break;
			}
			matched = grade;
		}
		return matched;
	}

	public Set<PredictionAchievementBadge> eligibleBadges(
		long predictionCount,
		long hitCount,
		long pinpointCount,
		PredictionAchievementGrade grade
	) {
		if (predictionCount < 0 || hitCount < 0 || pinpointCount < 0) {
			throw new IllegalArgumentException(
				"achievement counts must not be negative"
			);
		}
		if (hitCount > predictionCount || pinpointCount > hitCount) {
			throw new IllegalArgumentException(
				"achievement counts are inconsistent"
			);
		}
		if (grade == null) {
			throw new IllegalArgumentException("grade must not be null");
		}

		Set<PredictionAchievementBadge> badges =
			EnumSet.noneOf(PredictionAchievementBadge.class);
		if (hitCount >= 1) {
			badges.add(PredictionAchievementBadge.FIRST_HIT);
		}
		if (pinpointCount >= 1) {
			badges.add(PredictionAchievementBadge.PINPOINT);
		}
		if (hitCount >= 10) {
			badges.add(PredictionAchievementBadge.HIT_10);
		}
		if (predictionCount >= 50) {
			badges.add(PredictionAchievementBadge.PREDICTION_50);
		}
		if (pinpointCount >= 10) {
			badges.add(PredictionAchievementBadge.PINPOINT_10);
		}
		if (grade == PredictionAchievementGrade.ORACLE_5) {
			badges.add(PredictionAchievementBadge.ORACLE_MASTER);
		}
		return Set.copyOf(badges);
	}
}
