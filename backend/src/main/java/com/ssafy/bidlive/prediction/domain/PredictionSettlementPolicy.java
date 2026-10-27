package com.ssafy.bidlive.prediction.domain;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/** 낙찰가 대비 예측 오차율을 계산해 고정 배당 또는 전액 환불을 결정한다. */
public class PredictionSettlementPolicy {

	private static final BigInteger ONE_HUNDRED = BigInteger.valueOf(100);
	private static final int ERROR_RATE_SCALE = 6;
	private static final RoundingMode PAYOUT_ROUNDING = RoundingMode.DOWN;

	public PredictionSettlementDecision settle(
		long predictedPrice,
		long finalPrice,
		long stakeAmount
	) {
		requirePositive(predictedPrice, "predictedPrice");
		requirePositive(finalPrice, "finalPrice");
		requirePositive(stakeAmount, "stakeAmount");

		BigInteger difference = BigInteger.valueOf(predictedPrice)
			.subtract(BigInteger.valueOf(finalPrice))
			.abs();
		PredictionSettlementBand band = band(difference, finalPrice);
		BigDecimal errorRate = errorRate(difference, finalPrice);
		BigDecimal multiplier = band.rewardMultiplier();
		long payoutAmount = payout(stakeAmount, multiplier);
		PredictionResultStatus resultStatus =
			band == PredictionSettlementBand.MISS
				? PredictionResultStatus.MISS
				: PredictionResultStatus.HIT;

		return new PredictionSettlementDecision(
			resultStatus,
			band,
			errorRate,
			multiplier,
			payoutAmount
		);
	}

	public PredictionSettlementDecision refund(long stakeAmount) {
		requirePositive(stakeAmount, "stakeAmount");
		return new PredictionSettlementDecision(
			PredictionResultStatus.REFUNDED,
			PredictionSettlementBand.REFUNDED,
			null,
			PredictionSettlementBand.REFUNDED.rewardMultiplier(),
			stakeAmount
		);
	}

	private PredictionSettlementBand band(
		BigInteger difference,
		long finalPrice
	) {
		BigInteger errorRateNumerator = difference.multiply(ONE_HUNDRED);
		BigInteger finalPriceValue = BigInteger.valueOf(finalPrice);
		if (within(errorRateNumerator, finalPriceValue, 5)) {
			return PredictionSettlementBand.WITHIN_5;
		}
		if (within(errorRateNumerator, finalPriceValue, 10)) {
			return PredictionSettlementBand.WITHIN_10;
		}
		if (within(errorRateNumerator, finalPriceValue, 20)) {
			return PredictionSettlementBand.WITHIN_20;
		}
		return PredictionSettlementBand.MISS;
	}

	private boolean within(
		BigInteger errorRateNumerator,
		BigInteger finalPrice,
		int thresholdPercent
	) {
		return errorRateNumerator.compareTo(
			finalPrice.multiply(BigInteger.valueOf(thresholdPercent))
		) <= 0;
	}

	private BigDecimal errorRate(BigInteger difference, long finalPrice) {
		return new BigDecimal(difference.multiply(ONE_HUNDRED))
			.divide(
				BigDecimal.valueOf(finalPrice),
				ERROR_RATE_SCALE,
				RoundingMode.HALF_UP
			);
	}

	private long payout(long stakeAmount, BigDecimal multiplier) {
		return BigDecimal.valueOf(stakeAmount)
			.multiply(multiplier)
			.setScale(0, PAYOUT_ROUNDING)
			.longValueExact();
	}

	private void requirePositive(long value, String name) {
		if (value <= 0) {
			throw new IllegalArgumentException(name + " must be positive");
		}
	}
}
