package com.ssafy.bidlive.member.api.dto;

import com.ssafy.bidlive.member.application.ProfileActivitySummary;

public record ProfileActivitySummaryResponse(
	long salesCount,
	long purchaseCount,
	long predictionParticipationCount,
	long predictionHitCount,
	double predictionHitRate
) {

	public static ProfileActivitySummaryResponse from(
		ProfileActivitySummary summary
	) {
		return new ProfileActivitySummaryResponse(
			summary.salesCount(),
			summary.purchaseCount(),
			summary.predictionParticipationCount(),
			summary.predictionHitCount(),
			summary.predictionHitRate()
		);
	}
}
