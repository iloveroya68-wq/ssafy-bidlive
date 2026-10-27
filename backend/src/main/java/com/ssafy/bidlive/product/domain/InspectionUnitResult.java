package com.ssafy.bidlive.product.domain;

import java.util.List;

/**
 * 텍스트 또는 이미지 한 건의 검열 결과입니다.
 *
 * <p>{@code target}은 이미지일 때 대상 URL이고 텍스트일 때는 비어 있다. {@code failureReason}은
 * AI 판정을 얻지 못해 보류된 경우의 사유이며, 판정이 정상일 때는 비어 있다.
 */
public record InspectionUnitResult(
	InspectionVerdict verdict,
	String target,
	List<InspectionCategory> flaggedCategories,
	double maxScore,
	String reason,
	String failureReason
) {

	public InspectionUnitResult {
		if (verdict == null) {
			throw new IllegalArgumentException("Inspection verdict must not be null");
		}
		flaggedCategories = flaggedCategories == null ? List.of() : List.copyOf(flaggedCategories);
	}

	public static InspectionUnitResult judged(
		InspectionVerdict verdict,
		String target,
		List<InspectionCategory> flaggedCategories,
		double maxScore,
		String reason
	) {
		return new InspectionUnitResult(verdict, target, flaggedCategories, maxScore, reason, null);
	}

	/** AI 판정을 얻지 못한 경우이며 절대 통과로 바뀌지 않는다. */
	public static InspectionUnitResult heldForReview(String target, String failureReason) {
		return new InspectionUnitResult(
			InspectionVerdict.NEEDS_REVIEW,
			target,
			List.of(),
			0.0,
			"검열 판정을 얻지 못해 수동 검토가 필요합니다.",
			failureReason
		);
	}
}
