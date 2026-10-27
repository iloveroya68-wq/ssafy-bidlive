package com.ssafy.bidlive.product.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** 임계값 경계 판정과 "판단 근거 없음"이 통과로 새지 않는 정책을 검증합니다. */
class InspectionPolicyTest {

	private final InspectionPolicy policy = new InspectionPolicy(0.8, 0.4);

	@ParameterizedTest
	@CsvSource({
		"0.0, PASSED",
		"0.39, PASSED",
		"0.4, NEEDS_REVIEW",
		"0.79, NEEDS_REVIEW",
		"0.8, REJECTED",
		"1.0, REJECTED"
	})
	void decidesVerdictByHighestScore(double score, InspectionVerdict expected) {
		List<CategoryScore> scores = List.of(
			new CategoryScore(InspectionCategory.ILLEGAL_GOODS, score),
			new CategoryScore(InspectionCategory.SPAM, 0.0)
		);

		assertThat(policy.decide(scores)).isEqualTo(expected);
	}

	/** 점수가 없으면 판단 근거가 없다는 뜻이므로 통과시키지 않는다. */
	@Test
	void holdsForReviewWhenNoScoreIsAvailable() {
		assertThat(policy.decide(List.of())).isEqualTo(InspectionVerdict.NEEDS_REVIEW);
		assertThat(policy.decide(null)).isEqualTo(InspectionVerdict.NEEDS_REVIEW);
	}

	@Test
	void exposesOnlyCategoriesAtOrAboveReviewThresholdOrderedByScore() {
		List<CategoryScore> scores = List.of(
			new CategoryScore(InspectionCategory.SPAM, 0.39),
			new CategoryScore(InspectionCategory.ILLEGAL_GOODS, 0.9),
			new CategoryScore(InspectionCategory.COUNTERFEIT, 0.5)
		);

		assertThat(policy.flaggedCategories(scores)).containsExactly(
			InspectionCategory.ILLEGAL_GOODS,
			InspectionCategory.COUNTERFEIT
		);
		assertThat(policy.maxScore(scores)).isEqualTo(0.9);
	}

	@Test
	void rejectsThresholdsThatWouldWidenAutomaticApproval() {
		assertThatThrownBy(() -> new InspectionPolicy(0.4, 0.8))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new InspectionPolicy(0.0, 0.0))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new InspectionPolicy(1.5, 0.4))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsScoreOutsideNormalizedRange() {
		assertThatThrownBy(() -> new CategoryScore(InspectionCategory.SPAM, 1.1))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new CategoryScore(InspectionCategory.SPAM, -0.1))
			.isInstanceOf(IllegalArgumentException.class);
	}
}
