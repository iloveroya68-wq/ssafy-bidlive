package com.ssafy.bidlive.product.domain;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 한 번의 검열 요청에 대한 감사 기록이며 최종 판정과 단위별 결과를 함께 보관합니다.
 *
 * <p>{@code contentHash}는 같은 콘텐츠의 재요청을 알아보는 캐시 키다. 보류(NEEDS_REVIEW)는
 * 판정을 얻지 못한 상태일 수 있으므로 캐시 재사용 대상으로 삼지 않는다.
 */
public record ProductInspection(
	Long requesterId,
	String contentHash,
	InspectionVerdict verdict,
	InspectionUnitResult textResult,
	List<InspectionUnitResult> imageResults,
	OffsetDateTime inspectedAt
) {

	public ProductInspection {
		if (requesterId == null || requesterId <= 0) {
			throw new IllegalArgumentException("Inspection requesterId must be positive");
		}
		if (contentHash == null || contentHash.isBlank()) {
			throw new IllegalArgumentException("Inspection contentHash must not be blank");
		}
		if (verdict == null) {
			throw new IllegalArgumentException("Inspection verdict must not be null");
		}
		if (inspectedAt == null) {
			throw new IllegalArgumentException("Inspection inspectedAt must not be null");
		}
		imageResults = imageResults == null ? List.of() : List.copyOf(imageResults);
	}

	public boolean isReusable() {
		return verdict != InspectionVerdict.NEEDS_REVIEW;
	}
}
