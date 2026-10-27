package com.ssafy.bidlive.prediction.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;

import org.junit.jupiter.api.Test;

/** 예측 정확도 점수와 누적 성과별 등급·배지 기준을 검증한다. */
class PredictionAchievementPolicyTest {

	private final PredictionAchievementPolicy policy =
		new PredictionAchievementPolicy();

	@Test
	void assignsScoreBySettlementBand() {
		assertThat(policy.scoreFor(PredictionSettlementBand.WITHIN_5))
			.isEqualTo(100);
		assertThat(policy.scoreFor(PredictionSettlementBand.WITHIN_10))
			.isEqualTo(50);
		assertThat(policy.scoreFor(PredictionSettlementBand.WITHIN_20))
			.isEqualTo(20);
		assertThat(policy.scoreFor(PredictionSettlementBand.MISS)).isZero();
		assertThat(policy.scoreFor(PredictionSettlementBand.REFUNDED))
			.isZero();
	}

	@Test
	void assignsGradeAtEveryThreshold() {
		assertThat(policy.gradeFor(0))
			.isEqualTo(PredictionAchievementGrade.ORACLE_1);
		assertThat(policy.gradeFor(299))
			.isEqualTo(PredictionAchievementGrade.ORACLE_1);
		assertThat(policy.gradeFor(300))
			.isEqualTo(PredictionAchievementGrade.ORACLE_2);
		assertThat(policy.gradeFor(999))
			.isEqualTo(PredictionAchievementGrade.ORACLE_2);
		assertThat(policy.gradeFor(1_000))
			.isEqualTo(PredictionAchievementGrade.ORACLE_3);
		assertThat(policy.gradeFor(2_500))
			.isEqualTo(PredictionAchievementGrade.ORACLE_4);
		assertThat(policy.gradeFor(5_000))
			.isEqualTo(PredictionAchievementGrade.ORACLE_5);
	}

	@Test
	void findsEveryEligibleBadgeFromAccumulatedProgress() {
		assertThat(policy.eligibleBadges(
			50,
			10,
			10,
			PredictionAchievementGrade.ORACLE_5
		)).containsExactlyInAnyOrderElementsOf(Set.of(
			PredictionAchievementBadge.FIRST_HIT,
			PredictionAchievementBadge.PINPOINT,
			PredictionAchievementBadge.HIT_10,
			PredictionAchievementBadge.PREDICTION_50,
			PredictionAchievementBadge.PINPOINT_10,
			PredictionAchievementBadge.ORACLE_MASTER
		));
	}

	@Test
	void rejectsInvalidAchievementState() {
		assertThatThrownBy(() -> policy.gradeFor(-1))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("score must not be negative");
		assertThatThrownBy(() -> policy.eligibleBadges(
			1,
			2,
			0,
			PredictionAchievementGrade.ORACLE_1
		)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("achievement counts are inconsistent");
	}
}
