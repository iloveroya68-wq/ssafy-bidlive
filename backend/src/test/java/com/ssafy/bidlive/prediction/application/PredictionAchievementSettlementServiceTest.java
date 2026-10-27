package com.ssafy.bidlive.prediction.application;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import com.ssafy.bidlive.prediction.application.PredictionAchievementSettlementPersistence.GradeState;
import com.ssafy.bidlive.prediction.application.PredictionAchievementSettlementPersistence.Progress;
import com.ssafy.bidlive.prediction.domain.PredictionResultStatus;
import com.ssafy.bidlive.prediction.domain.PredictionSettlementBand;
import com.ssafy.bidlive.prediction.domain.PredictionSettlementDecision;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 최초 정산 결과의 점수·등급 누적과 충족 배지의 멱등 저장 요청을 검증한다. */
@ExtendWith(MockitoExtension.class)
class PredictionAchievementSettlementServiceTest {

	@Mock
	private PredictionAchievementSettlementPersistence persistence;

	private PredictionAchievementSettlementService service;

	@BeforeEach
	void setUp() {
		service = new PredictionAchievementSettlementService(persistence);
	}

	@Test
	void accumulatesScoreAndRequestsAllEligibleBadges() {
		when(persistence.lockOrCreateGrade(25L))
			.thenReturn(new GradeState("ORACLE_1", 4_900L));
		when(persistence.findProgress(25L))
			.thenReturn(new Progress(50L, 10L, 10L));

		service.apply(
			25L,
			801L,
			decision(
				PredictionResultStatus.HIT,
				PredictionSettlementBand.WITHIN_5,
				5,
				1_500L
			)
		);

		verify(persistence).updateGrade(25L, "ORACLE_5", 5_000L);
		verify(persistence).saveBadgeIfAbsent(25L, "FIRST_HIT", 801L);
		verify(persistence).saveBadgeIfAbsent(25L, "PINPOINT", 801L);
		verify(persistence).saveBadgeIfAbsent(25L, "HIT_10", 801L);
		verify(persistence).saveBadgeIfAbsent(
			25L,
			"PREDICTION_50",
			801L
		);
		verify(persistence).saveBadgeIfAbsent(25L, "PINPOINT_10", 801L);
		verify(persistence).saveBadgeIfAbsent(
			25L,
			"ORACLE_MASTER",
			801L
		);
	}

	@Test
	void recordsMissWithoutIncreasingScoreOrAwardingBadge() {
		when(persistence.lockOrCreateGrade(25L))
			.thenReturn(new GradeState("ORACLE_1", 0L));
		when(persistence.findProgress(25L))
			.thenReturn(new Progress(1L, 0L, 0L));

		service.apply(
			25L,
			801L,
			decision(
				PredictionResultStatus.MISS,
				PredictionSettlementBand.MISS,
				0,
				0L
			)
		);

		verify(persistence).updateGrade(25L, "ORACLE_1", 0L);
		verify(persistence, never()).saveBadgeIfAbsent(
			org.mockito.ArgumentMatchers.any(),
			org.mockito.ArgumentMatchers.any(),
			org.mockito.ArgumentMatchers.any()
		);
	}

	@Test
	void ignoresRefundedPrediction() {
		service.apply(
			25L,
			801L,
			decision(
				PredictionResultStatus.REFUNDED,
				PredictionSettlementBand.REFUNDED,
				1,
				300L
			)
		);

		verify(persistence, never()).lockOrCreateGrade(25L);
	}

	private PredictionSettlementDecision decision(
		PredictionResultStatus status,
		PredictionSettlementBand band,
		int multiplier,
		long payoutAmount
	) {
		return new PredictionSettlementDecision(
			status,
			band,
			status == PredictionResultStatus.REFUNDED
				? null
				: BigDecimal.ONE,
			multiplier,
			payoutAmount
		);
	}
}
