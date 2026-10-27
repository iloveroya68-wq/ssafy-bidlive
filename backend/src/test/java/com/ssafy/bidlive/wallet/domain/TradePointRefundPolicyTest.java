package com.ssafy.bidlive.wallet.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TradePointRefundPolicyTest {

	@Test
	void acceptsOnlyPositiveAmounts() {
		assertThat(TradePointRefundPolicy.isPositive(1L)).isTrue();
		assertThat(TradePointRefundPolicy.isPositive(0L)).isFalse();
		assertThat(TradePointRefundPolicy.isPositive(-1L)).isFalse();
		assertThat(TradePointRefundPolicy.isPositive(null)).isFalse();
	}
}
