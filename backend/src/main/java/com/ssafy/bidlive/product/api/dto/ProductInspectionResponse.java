package com.ssafy.bidlive.product.api.dto;

import java.time.OffsetDateTime;
import java.util.List;

import com.ssafy.bidlive.product.domain.InspectionCategory;
import com.ssafy.bidlive.product.domain.InspectionUnitResult;
import com.ssafy.bidlive.product.domain.InspectionVerdict;
import com.ssafy.bidlive.product.domain.ProductInspection;

/** 검열 결과를 프로젝트 공통 `type`·`data` 형식으로 감싸 반환합니다. */
public record ProductInspectionResponse(
	ProductInspectionEventType type,
	ProductInspectionData data
) {

	public static ProductInspectionResponse from(ProductInspection inspection) {
		return new ProductInspectionResponse(
			ProductInspectionEventType.AI_INSPECTION_RESULT,
			new ProductInspectionData(
				inspection.verdict(),
				UnitResult.from(inspection.textResult()),
				inspection.imageResults().stream().map(UnitResult::from).toList(),
				inspection.inspectedAt()
			)
		);
	}

	public record ProductInspectionData(
		InspectionVerdict verdict,
		UnitResult textResult,
		List<UnitResult> imageResults,
		OffsetDateTime inspectedAt
	) {
	}

	/**
	 * {@code imageUrl}은 이미지 결과에서만 채워진다. {@code failureReason}은 AI 판정을 얻지 못해
	 * 보류된 경우의 사유이며 정상 판정에서는 비어 있다.
	 */
	public record UnitResult(
		InspectionVerdict verdict,
		String imageUrl,
		List<InspectionCategory> flaggedCategories,
		double maxScore,
		String reason,
		String failureReason
	) {

		static UnitResult from(InspectionUnitResult result) {
			if (result == null) {
				return null;
			}

			return new UnitResult(
				result.verdict(),
				result.target(),
				result.flaggedCategories(),
				result.maxScore(),
				result.reason(),
				result.failureReason()
			);
		}
	}
}
