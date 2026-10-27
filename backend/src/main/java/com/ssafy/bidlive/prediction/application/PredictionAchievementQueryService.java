package com.ssafy.bidlive.prediction.application;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.ssafy.bidlive.member.application.MemberPersistence;
import com.ssafy.bidlive.prediction.application.PredictionAchievementPersistence.Badge;
import com.ssafy.bidlive.prediction.application.PredictionAchievementPersistence.Grade;
import com.ssafy.bidlive.prediction.application.PredictionAchievementPersistence.Statistics;
import com.ssafy.bidlive.prediction.domain.PredictionAchievementBadge;
import com.ssafy.bidlive.prediction.domain.PredictionAchievementErrorCode;
import com.ssafy.bidlive.prediction.domain.PredictionAchievementGrade;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 인증 회원의 정산된 예측 통계와 저장된 명예 등급·뱃지를 조회한다. */
@Service
public class PredictionAchievementQueryService {

	private static final String INITIAL_GRADE_CODE = "ORACLE_1";

	private final MemberPersistence memberPersistence;
	private final PredictionAchievementPersistence achievementPersistence;

	public PredictionAchievementQueryService(
		MemberPersistence memberPersistence,
		PredictionAchievementPersistence achievementPersistence
	) {
		this.memberPersistence = memberPersistence;
		this.achievementPersistence = achievementPersistence;
	}

	@Transactional(readOnly = true)
	public PredictionAchievementResult getPredictionAchievements(Long memberId) {
		if (memberId == null || memberPersistence.findById(memberId).isEmpty()) {
			throw new PredictionAchievementException(
				PredictionAchievementErrorCode.MEMBER_NOT_FOUND
			);
		}

		Statistics statistics = achievementPersistence.findStatistics(memberId);
		Grade grade = achievementPersistence.findGrade(memberId)
			.orElseGet(() -> new Grade(INITIAL_GRADE_CODE, 0L));
		return new PredictionAchievementResult(
			memberId,
			Math.toIntExact(statistics.predictionCount()),
			Math.toIntExact(statistics.hitCount()),
			hitRate(statistics),
			statistics.totalStakedPoints(),
			statistics.totalRewardPoints(),
			new PredictionAchievementResult.Grade(
				grade.code(),
				gradeName(grade.code()),
				grade.score()
			),
			achievementPersistence.findBadges(memberId).stream()
				.map(this::toResultBadge)
				.toList()
		);
	}

	private BigDecimal hitRate(Statistics statistics) {
		if (statistics.predictionCount() == 0) {
			return BigDecimal.ZERO.setScale(1);
		}
		return BigDecimal.valueOf(statistics.hitCount())
			.multiply(BigDecimal.valueOf(100))
			.divide(
				BigDecimal.valueOf(statistics.predictionCount()),
				1,
				RoundingMode.HALF_UP
			);
	}

	private PredictionAchievementResult.Badge toResultBadge(Badge badge) {
		return new PredictionAchievementResult.Badge(
			badge.code(),
			badgeName(badge.code()),
			badge.earnedAt()
		);
	}

	private String gradeName(String code) {
		return PredictionAchievementGrade.fromCode(code)
			.map(PredictionAchievementGrade::displayName)
			.orElse(code);
	}

	private String badgeName(String code) {
		return PredictionAchievementBadge.fromCode(code)
			.map(PredictionAchievementBadge::displayName)
			.orElse(code);
	}
}
