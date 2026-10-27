package com.ssafy.bidlive.member.application;

public record ProfileActivitySummary(
	long salesCount,
	long purchaseCount,
	long predictionParticipationCount,
	long predictionHitCount,
	double predictionHitRate
) {
}
