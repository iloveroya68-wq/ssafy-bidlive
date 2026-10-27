package com.ssafy.bidlive.product.domain;

import java.util.Comparator;
import java.util.List;

/**
 * 카테고리 점수를 임계값과 비교해 단위 판정을 만드는 정책입니다.
 *
 * <p>임계값은 외부 설정에서 주입한다. 어떤 값이 와도 자동 통과가 늘어나지 않도록 두 임계값의
 * 순서를 생성 시점에 검증한다.
 */
public class InspectionPolicy {

	private final double rejectThreshold;
	private final double reviewThreshold;

	public InspectionPolicy(double rejectThreshold, double reviewThreshold) {
		if (rejectThreshold <= 0.0 || rejectThreshold > 1.0) {
			throw new IllegalArgumentException("rejectThreshold must be within (0.0, 1.0]");
		}
		if (reviewThreshold <= 0.0 || reviewThreshold > 1.0) {
			throw new IllegalArgumentException("reviewThreshold must be within (0.0, 1.0]");
		}
		if (reviewThreshold > rejectThreshold) {
			throw new IllegalArgumentException(
				"reviewThreshold must not be greater than rejectThreshold");
		}

		this.rejectThreshold = rejectThreshold;
		this.reviewThreshold = reviewThreshold;
	}

	/**
	 * 가장 높은 점수 하나로 판정한다. 임계값 이상이면 REJECTED, 경계 구간이면 NEEDS_REVIEW다.
	 * 점수가 비어 있으면 판단 근거가 없다는 뜻이므로 통과시키지 않고 NEEDS_REVIEW로 둔다.
	 */
	public InspectionVerdict decide(List<CategoryScore> scores) {
		if (scores == null || scores.isEmpty()) {
			return InspectionVerdict.NEEDS_REVIEW;
		}

		double maxScore = maxScore(scores);
		if (maxScore >= rejectThreshold) {
			return InspectionVerdict.REJECTED;
		}
		if (maxScore >= reviewThreshold) {
			return InspectionVerdict.NEEDS_REVIEW;
		}
		return InspectionVerdict.PASSED;
	}

	/** 응답에 노출할 위험 카테고리는 검토 임계값을 넘은 것만 남긴다. */
	public List<InspectionCategory> flaggedCategories(List<CategoryScore> scores) {
		if (scores == null) {
			return List.of();
		}

		return scores.stream()
			.filter(score -> score.score() >= reviewThreshold)
			.sorted(Comparator.comparingDouble(CategoryScore::score).reversed())
			.map(CategoryScore::category)
			.toList();
	}

	public double maxScore(List<CategoryScore> scores) {
		if (scores == null || scores.isEmpty()) {
			return 0.0;
		}

		return scores.stream()
			.mapToDouble(CategoryScore::score)
			.max()
			.orElse(0.0);
	}
}
