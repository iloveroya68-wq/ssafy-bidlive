package com.ssafy.bidlive.product.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/** 하나라도 거부면 거부, 보류가 남아 있으면 통과가 되지 않는 집계 규칙을 검증합니다. */
class InspectionVerdictTest {

	@Test
	void rejectsWhenAnyUnitIsRejected() {
		assertThat(InspectionVerdict.aggregate(List.of(
			InspectionVerdict.PASSED,
			InspectionVerdict.NEEDS_REVIEW,
			InspectionVerdict.REJECTED
		))).isEqualTo(InspectionVerdict.REJECTED);
	}

	@Test
	void holdsForReviewWhenAnyUnitIsHeld() {
		assertThat(InspectionVerdict.aggregate(List.of(
			InspectionVerdict.PASSED,
			InspectionVerdict.NEEDS_REVIEW
		))).isEqualTo(InspectionVerdict.NEEDS_REVIEW);
	}

	@Test
	void passesOnlyWhenEveryUnitPasses() {
		assertThat(InspectionVerdict.aggregate(List.of(
			InspectionVerdict.PASSED,
			InspectionVerdict.PASSED
		))).isEqualTo(InspectionVerdict.PASSED);
	}

	/** 검열한 대상이 없으면 자동 통과를 만들지 않는다. */
	@Test
	void holdsForReviewWhenNothingWasInspected() {
		assertThat(InspectionVerdict.aggregate(List.of()))
			.isEqualTo(InspectionVerdict.NEEDS_REVIEW);
	}
}
