package com.ssafy.bidlive.prediction.api.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import com.ssafy.bidlive.prediction.application.PredictionAchievementResult;

/** 로그인 회원의 정산된 예측 통계와 현재 등급 및 획득 뱃지를 반환합니다. */
public record PredictionAchievementResponse(
	Long memberId,
	Integer predictionCount,
	Integer hitCount,
	BigDecimal hitRate,
	Long totalStakedPoints,
	Long totalRewardPoints,
	Grade grade,
	List<Badge> badges
) {

	public PredictionAchievementResponse {
		badges = List.copyOf(badges);
	}

	public static PredictionAchievementResponse from(
		PredictionAchievementResult result
	) {
		return new PredictionAchievementResponse(
			result.memberId(),
			result.predictionCount(),
			result.hitCount(),
			result.hitRate(),
			result.totalStakedPoints(),
			result.totalRewardPoints(),
			new Grade(
				result.grade().code(),
				result.grade().name(),
				result.grade().score()
			),
			result.badges().stream()
				.map(badge -> new Badge(
					badge.code(),
					badge.name(),
					badge.earnedAt()
				))
				.toList()
		);
	}

	public record Grade(
		String code,
		String name,
		Long score
	) {
	}

	public record Badge(
		String badgeCode,
		String name,
		OffsetDateTime earnedAt
	) {
	}
}
