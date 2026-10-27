package com.ssafy.bidlive.common.support.redis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

/** 전역 Wallet Key 분리 후 기존 입찰·예측 Redis 계약이 유지되는지 검증한다. */
class RealtimeWalletRedisKeyTest {

	@Test
	void providesTradeAndBettingWalletKeysInSharedHashSlot() {
		assertThat(RealtimeWalletRedisKey.tradeAvailableBalancesKey())
			.isEqualTo("bid:{bid}:wallet:available-balances");
		assertThat(RealtimeWalletRedisKey.tradeHeldBalancesKey())
			.isEqualTo("bid:{bid}:wallet:held-balances");
		assertThat(RealtimeWalletRedisKey.bettingAvailableBalancesKey())
			.isEqualTo("bid:{bid}:wallet:betting-available-balances");
	}

	@Test
	void keepsBidProcessingKeyOrderAndValuesCompatible() {
		assertThat(RealtimeAuctionRedisKey.bidProcessingKeys(100L))
			.containsExactly(
				"bid:{bid}:auction:100:state",
				"bid:{bid}:auction:100:member-bids",
				"bid:{bid}:auction:100:submitted-at",
				"bid:{bid}:auction:100:participation",
				"bid:{bid}:wallet:available-balances",
				"bid:{bid}:wallet:held-balances",
				"bid:{bid}:auction:100:processed-requests"
			);
	}

	@Test
	void keepsPredictionParticipationKeyOrderAndValuesCompatible() {
		UUID requestId = UUID.fromString(
			"9a61c1e4-7ad4-4e7c-83b5-a63edc00ac20"
		);

		assertThat(RealtimeAuctionRedisKey.predictionParticipationKeys(
			100L,
			requestId
		)).containsExactly(
			"bid:{bid}:auction:100:state",
			"bid:{bid}:auction:100:participation",
			"bid:{bid}:prediction:processed-request:" + requestId,
			"bid:{bid}:wallet:betting-available-balances"
		);
	}
}
