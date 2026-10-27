package com.ssafy.bidlive.prediction.domain;

import java.math.BigDecimal;

/** 한 예측의 오차 구간과 배수, 최종 지급액을 함께 표현한다. */
public record PredictionSettlementDecision(
	PredictionResultStatus resultStatus,
	PredictionSettlementBand band,
	BigDecimal errorRate,
	BigDecimal rewardMultiplier,
	long payoutAmount
) {

	public PredictionSettlementDecision(
		PredictionResultStatus resultStatus,
		PredictionSettlementBand band,
		BigDecimal errorRate,
		int rewardMultiplier,
		long payoutAmount
	) {
		this(
			resultStatus,
			band,
			errorRate,
			BigDecimal.valueOf(rewardMultiplier),
			payoutAmount
		);
	}

	public PredictionSettlementDecision {
		if (rewardMultiplier != null) {
			rewardMultiplier = rewardMultiplier.stripTrailingZeros();
		}
		if (resultStatus == null) {
			throw new IllegalArgumentException("resultStatus must not be null");
		}
		if (band == null) {
			throw new IllegalArgumentException("band must not be null");
		}
		if (rewardMultiplier == null
			|| !matchesBandMultiplier(resultStatus, band, rewardMultiplier)) {
			throw new IllegalArgumentException(
				"rewardMultiplier must match settlement band"
			);
		}
		if (payoutAmount < 0) {
			throw new IllegalArgumentException(
				"payoutAmount must not be negative"
			);
		}
		validateState(
			resultStatus,
			band,
			errorRate,
			payoutAmount
		);
	}

	private static boolean matchesBandMultiplier(
		PredictionResultStatus resultStatus,
		PredictionSettlementBand band,
		BigDecimal rewardMultiplier
	) {
		return rewardMultiplier.compareTo(band.rewardMultiplier()) == 0
			|| (resultStatus == PredictionResultStatus.HIT
				&& band == PredictionSettlementBand.WITHIN_5
				&& rewardMultiplier.compareTo(BigDecimal.ONE) == 0);
	}

	private static void validateState(
		PredictionResultStatus resultStatus,
		PredictionSettlementBand band,
		BigDecimal errorRate,
		long payoutAmount
	) {
		if (resultStatus == PredictionResultStatus.REFUNDED) {
			if (band != PredictionSettlementBand.REFUNDED
				|| errorRate != null || payoutAmount <= 0) {
				throw new IllegalArgumentException(
					"refunded settlement state is invalid"
				);
			}
			return;
		}

		if (errorRate == null || errorRate.signum() < 0) {
			throw new IllegalArgumentException(
				"evaluated settlement requires a non-negative errorRate"
			);
		}
		if (resultStatus == PredictionResultStatus.MISS) {
			if (band != PredictionSettlementBand.MISS || payoutAmount != 0) {
				throw new IllegalArgumentException(
					"miss settlement state is invalid"
				);
			}
			return;
		}
		if (band == PredictionSettlementBand.MISS
			|| band == PredictionSettlementBand.REFUNDED
			|| payoutAmount <= 0) {
			throw new IllegalArgumentException(
				"hit settlement state is invalid"
			);
		}
	}
}
