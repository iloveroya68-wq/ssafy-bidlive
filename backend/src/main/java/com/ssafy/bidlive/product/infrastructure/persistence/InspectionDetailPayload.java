package com.ssafy.bidlive.product.infrastructure.persistence;

import java.util.List;

/**
 * JSONB 컬럼에 저장하는 단위 판정 상세의 직렬화 형태입니다.
 *
 * <p>도메인 레코드를 그대로 저장하지 않는 이유는, 도메인 구조가 바뀔 때 과거 감사 기록을 읽지
 * 못하게 되는 상황을 만들지 않기 위해서다.
 */
record InspectionDetailPayload(
	Unit text,
	List<Unit> images
) {

	record Unit(
		String verdict,
		String target,
		List<String> flaggedCategories,
		double maxScore,
		String reason,
		String failureReason
	) {
	}
}
