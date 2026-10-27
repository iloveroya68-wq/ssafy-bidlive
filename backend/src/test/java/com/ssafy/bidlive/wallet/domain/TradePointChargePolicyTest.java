package com.ssafy.bidlive.wallet.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

/** 거래 포인트 충전 금액 한도와 한국 시간 일일 구간을 검증한다. */
class TradePointChargePolicyTest {

	@Test
	void validatesPositiveAmountAndSingleChargeLimit() {
		assertThat(TradePointChargePolicy.isPositive(null)).isFalse();
		assertThat(TradePointChargePolicy.isPositive(0L)).isFalse();
		assertThat(TradePointChargePolicy.isPositive(1L)).isTrue();
		assertThat(TradePointChargePolicy.exceedsSingleChargeLimit(
			1_000_000L
		)).isFalse();
		assertThat(TradePointChargePolicy.exceedsSingleChargeLimit(
			1_000_001L
		)).isTrue();
	}

	@Test
	void appliesDailyLimitWithoutOverflow() {
		assertThat(TradePointChargePolicy.exceedsDailyChargeLimit(
			2_000_000L,
			1_000_000L
		)).isFalse();
		assertThat(TradePointChargePolicy.exceedsDailyChargeLimit(
			2_000_001L,
			1_000_000L
		)).isTrue();
		assertThat(TradePointChargePolicy.exceedsDailyChargeLimit(
			Long.MAX_VALUE,
			1L
		)).isTrue();
	}

	@Test
	void createsDailyWindowFromKoreanMidnight() {
		var window = TradePointChargePolicy.dailyWindow(
			Instant.parse("2026-07-20T15:30:00Z")
		);

		assertThat(window.startInclusive())
			.isEqualTo(Instant.parse("2026-07-20T15:00:00Z"));
		assertThat(window.endExclusive())
			.isEqualTo(Instant.parse("2026-07-21T15:00:00Z"));
	}
}
