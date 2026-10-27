package com.ssafy.bidlive.prediction.application;

import com.ssafy.bidlive.prediction.application.PredictionAchievementSettlementPersistence.GradeState;
import com.ssafy.bidlive.prediction.application.PredictionAchievementSettlementPersistence.Progress;
import com.ssafy.bidlive.prediction.domain.PredictionAchievementBadge;
import com.ssafy.bidlive.prediction.domain.PredictionAchievementGrade;
import com.ssafy.bidlive.prediction.domain.PredictionAchievementPolicy;
import com.ssafy.bidlive.prediction.domain.PredictionResultStatus;
import com.ssafy.bidlive.prediction.domain.PredictionSettlementDecision;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 최초 생성된 예측 결과의 점수·등급을 누적하고 새로 충족한 배지를 기록한다. */
@Service
public class PredictionAchievementSettlementService {

	private final PredictionAchievementSettlementPersistence persistence;
	private final PredictionAchievementPolicy policy;

	public PredictionAchievementSettlementService(
		PredictionAchievementSettlementPersistence persistence
	) {
		this.persistence = persistence;
		this.policy = new PredictionAchievementPolicy();
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void apply(
		Long memberId,
		Long predictionResultId,
		PredictionSettlementDecision decision
	) {
		if (decision == null) {
			throw new IllegalArgumentException("decision must not be null");
		}
		if (decision.resultStatus() == PredictionResultStatus.REFUNDED) {
			return;
		}

		GradeState current = persistence.lockOrCreateGrade(memberId);
		long nextScore = Math.addExact(
			current.gradeScore(),
			policy.scoreFor(decision.band())
		);
		PredictionAchievementGrade nextGrade =
			policy.gradeFor(nextScore);
		persistence.updateGrade(
			memberId,
			nextGrade.name(),
			nextScore
		);

		Progress progress = persistence.findProgress(memberId);
		for (PredictionAchievementBadge badge : policy.eligibleBadges(
			progress.predictionCount(),
			progress.hitCount(),
			progress.pinpointCount(),
			nextGrade
		)) {
			persistence.saveBadgeIfAbsent(
				memberId,
				badge.name(),
				predictionResultId
			);
		}
	}
}
