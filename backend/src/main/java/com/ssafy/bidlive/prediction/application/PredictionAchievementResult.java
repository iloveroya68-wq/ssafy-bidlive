package com.ssafy.bidlive.prediction.application;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/** 인증 회원의 정산된 예측 통계와 명예 등급·뱃지 조회 결과다. */
public record PredictionAchievementResult(
	Long memberId,
	Integer predictionCount,
	Integer hitCount,
	BigDecimal hitRate,
	Long totalStakedPoints,
	Long totalRewardPoints,
	Grade grade,
	List<Badge> badges
) {

	public PredictionAchievementResult {
		badges = List.copyOf(badges);
	}

	public record Grade(
		String code,
		String name,
		Long score
	) {
	}

	public record Badge(
		String code,
		String name,
		OffsetDateTime earnedAt
	) {
	}
}
