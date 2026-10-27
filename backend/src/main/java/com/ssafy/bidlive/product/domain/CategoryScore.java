package com.ssafy.bidlive.product.domain;

/** 카테고리별 위험도이며 0.0 이상 1.0 이하로 정규화됩니다. */
public record CategoryScore(
	InspectionCategory category,
	double score
) {

	public CategoryScore {
		if (category == null) {
			throw new IllegalArgumentException("Inspection category must not be null");
		}
		if (Double.isNaN(score) || score < 0.0 || score > 1.0) {
			throw new IllegalArgumentException("Inspection score must be between 0.0 and 1.0");
		}
	}
}
