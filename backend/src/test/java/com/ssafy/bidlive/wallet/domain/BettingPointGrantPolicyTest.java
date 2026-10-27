package com.ssafy.bidlive.wallet.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class BettingPointGrantPolicyTest {

	@Test
	void exposesVersionedServerAmountsForEveryGrantType() {
		assertThat(BettingPointGrantPolicy.VERSION)
			.isEqualTo("2026-07-01");
		assertThat(BettingPointGrantPolicy.amountFor(
			BettingPointGrantType.SIGNUP
		)).isEqualTo(1_000L);
		assertThat(BettingPointGrantPolicy.amountFor(
			BettingPointGrantType.DAILY_ATTENDANCE
		)).isEqualTo(100L);
		assertThat(BettingPointGrantPolicy.amountFor(
			BettingPointGrantType.LIVE_WATCH
		)).isEqualTo(100L);
	}

	@Test
	void rejectsAmountOrVersionThatDoesNotMatchPolicy() {
		assertThat(BettingPointGrantPolicy.matches(
			BettingPointGrantType.LIVE_WATCH,
			100L,
			"2026-07-01"
		)).isTrue();
		assertThat(BettingPointGrantPolicy.matches(
			BettingPointGrantType.LIVE_WATCH,
			101L,
			"2026-07-01"
		)).isFalse();
		assertThat(BettingPointGrantPolicy.matches(
			BettingPointGrantType.LIVE_WATCH,
			100L,
			"old-policy"
		)).isFalse();
	}

	@Test
	void validatesCanonicalSourceForEachGrantType() {
		Instant occurredAt = Instant.parse("2026-07-22T01:30:00Z");

		assertThat(BettingPointGrantPolicy.isValidSource(
			BettingPointGrantType.SIGNUP,
			25L,
			"MEMBER:25",
			occurredAt
		)).isTrue();
		assertThat(BettingPointGrantPolicy.isValidSource(
			BettingPointGrantType.DAILY_ATTENDANCE,
			25L,
			"DATE:2026-07-22",
			occurredAt
		)).isTrue();
		assertThat(BettingPointGrantPolicy.isValidSource(
			BettingPointGrantType.LIVE_WATCH,
			25L,
			"AUCTION:100",
			occurredAt
		)).isTrue();
		assertThat(BettingPointGrantPolicy.isValidSource(
			BettingPointGrantType.LIVE_WATCH,
			25L,
			"AUCTION:0",
			occurredAt
		)).isFalse();
	}
}
