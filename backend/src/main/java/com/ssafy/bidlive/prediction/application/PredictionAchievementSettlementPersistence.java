package com.ssafy.bidlive.prediction.application;

/** 예측 정산으로 발생한 누적 점수·등급과 새 배지를 원자적으로 반영하는 Port다. */
public interface PredictionAchievementSettlementPersistence {

	GradeState lockOrCreateGrade(Long memberId);

	void updateGrade(Long memberId, String gradeCode, long gradeScore);

	Progress findProgress(Long memberId);

	boolean saveBadgeIfAbsent(
		Long memberId,
		String badgeCode,
		Long predictionResultId
	);

	record GradeState(
		String gradeCode,
		long gradeScore
	) {
	}

	record Progress(
		long predictionCount,
		long hitCount,
		long pinpointCount
	) {
	}
}
