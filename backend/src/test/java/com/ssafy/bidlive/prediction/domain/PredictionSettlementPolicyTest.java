package com.ssafy.bidlive.prediction.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** PRED-03의 오차율 경계, 고정 배당 및 전액 환불 정책을 검증한다. */
class PredictionSettlementPolicyTest {

	private PredictionSettlementPolicy policy;

	@BeforeEach
	void setUp() {
		policy = new PredictionSettlementPolicy();
	}

	@Test
	void paysFiveTimesStakeAtExactlyFivePercentError() {
		PredictionSettlementDecision decision =
			policy.settle(10_500L, 10_000L, 300L);

		assertDecision(
			decision,
			PredictionResultStatus.HIT,
			PredictionSettlementBand.WITHIN_5,
			"5.000000",
			"5",
			1_500L
		);
	}

	@Test
	void paysThreeTimesStakeAtExactlyTenPercentError() {
		PredictionSettlementDecision decision =
			policy.settle(9_000L, 10_000L, 300L);

		assertDecision(
			decision,
			PredictionResultStatus.HIT,
			PredictionSettlementBand.WITHIN_10,
			"10.000000",
			"3",
			900L
		);
	}

	@Test
	void paysOnePointFiveTimesStakeAtExactlyTwentyPercentErrorAndRoundsDown() {
		PredictionSettlementDecision decision =
			policy.settle(12_000L, 10_000L, 301L);

		assertDecision(
			decision,
			PredictionResultStatus.HIT,
			PredictionSettlementBand.WITHIN_20,
			"20.000000",
			"1.5",
			451L
		);
	}

	@Test
	void missesImmediatelyAboveTwentyPercentError() {
		PredictionSettlementDecision decision =
			policy.settle(12_001L, 10_000L, 300L);

		assertDecision(
			decision,
			PredictionResultStatus.MISS,
			PredictionSettlementBand.MISS,
			"20.010000",
			"0",
			0L
		);
	}

	@Test
	void roundsStoredErrorRateWithoutUsingRoundedValueForBand() {
		PredictionSettlementDecision decision =
			policy.settle(12_000_001L, 10_000_000L, 300L);

		assertDecision(
			decision,
			PredictionResultStatus.MISS,
			PredictionSettlementBand.MISS,
			"20.000010",
			"0",
			0L
		);
	}

	@Test
	void calculatesErrorRateAboveOneHundredPercent() {
		PredictionSettlementDecision decision =
			policy.settle(300L, 100L, 300L);

		assertDecision(
			decision,
			PredictionResultStatus.MISS,
			PredictionSettlementBand.MISS,
			"200.000000",
			"0",
			0L
		);
	}

	@Test
	void refundsTheFullStakeWithoutErrorRate() {
		PredictionSettlementDecision decision = policy.refund(300L);

		assertThat(decision.resultStatus())
			.isEqualTo(PredictionResultStatus.REFUNDED);
		assertThat(decision.band())
			.isEqualTo(PredictionSettlementBand.REFUNDED);
		assertThat(decision.errorRate()).isNull();
		assertThat(decision.rewardMultiplier()).isEqualByComparingTo("1");
		assertThat(decision.payoutAmount()).isEqualTo(300L);
	}

	@Test
	void rejectsNonPositivePricesAndStake() {
		assertThatThrownBy(() -> policy.settle(0L, 10_000L, 300L))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("predictedPrice must be positive");
		assertThatThrownBy(() -> policy.settle(10_000L, 0L, 300L))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("finalPrice must be positive");
		assertThatThrownBy(() -> policy.settle(10_000L, 10_000L, 0L))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("stakeAmount must be positive");
		assertThatThrownBy(() -> policy.refund(0L))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("stakeAmount must be positive");
	}

	@Test
	void rejectsPayoutOverflow() {
		assertThatThrownBy(() -> policy.settle(
			10_000L,
			10_000L,
			Long.MAX_VALUE
		)).isInstanceOf(ArithmeticException.class);
	}

	private void assertDecision(
		PredictionSettlementDecision actual,
		PredictionResultStatus expectedStatus,
		PredictionSettlementBand expectedBand,
		String expectedErrorRate,
		String expectedMultiplier,
		long expectedPayoutAmount
	) {
		assertThat(actual.resultStatus()).isEqualTo(expectedStatus);
		assertThat(actual.band()).isEqualTo(expectedBand);
		assertThat(actual.errorRate())
			.isEqualByComparingTo(new BigDecimal(expectedErrorRate));
		assertThat(actual.rewardMultiplier())
			.isEqualByComparingTo(expectedMultiplier);
		assertThat(actual.payoutAmount()).isEqualTo(expectedPayoutAmount);
	}
}
