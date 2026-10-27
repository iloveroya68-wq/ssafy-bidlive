package com.ssafy.bidlive.product.domain;

/** 검열 판정 결과이며 이름이 그대로 응답과 이력에 저장됩니다. */
public enum InspectionVerdict {

	PASSED,
	NEEDS_REVIEW,
	REJECTED;

	/**
	 * 여러 판정을 하나로 합친다. 하나라도 REJECTED면 REJECTED, 경계나 보류가 있으면
	 * NEEDS_REVIEW가 되며 모두 통과일 때만 PASSED다. 검열 대상이 없으면 자동 통과를 만들지
	 * 않기 위해 NEEDS_REVIEW로 둔다.
	 */
	public static InspectionVerdict aggregate(Iterable<InspectionVerdict> verdicts) {
		boolean hasAny = false;
		boolean hasReview = false;

		for (InspectionVerdict verdict : verdicts) {
			hasAny = true;
			if (verdict == REJECTED) {
				return REJECTED;
			}
			if (verdict == NEEDS_REVIEW) {
				hasReview = true;
			}
		}

		if (!hasAny || hasReview) {
			return NEEDS_REVIEW;
		}
		return PASSED;
	}
}
