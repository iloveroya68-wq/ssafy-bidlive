package com.ssafy.bidlive.prediction.application;

public record MemberPredictionActivity(
	long participationCount,
	long hitCount
) {

	public MemberPredictionActivity {
		if (participationCount < 0 || hitCount < 0 || hitCount > participationCount) {
			throw new IllegalArgumentException("prediction activity counts are invalid");
		}
	}
}
