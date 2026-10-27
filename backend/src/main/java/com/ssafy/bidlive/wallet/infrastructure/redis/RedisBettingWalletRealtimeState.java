package com.ssafy.bidlive.wallet.infrastructure.redis;

import com.ssafy.bidlive.common.support.redis.RealtimeWalletRedisKey;
import com.ssafy.bidlive.wallet.application.BettingWalletRealtimeState;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/** Redis BETTING 가용 잔액을 최초 적재하고 정산 지급액을 증가시킨다. */
@Repository
public class RedisBettingWalletRealtimeState
	implements BettingWalletRealtimeState {

	private final StringRedisTemplate redisTemplate;

	public RedisBettingWalletRealtimeState(
		StringRedisTemplate redisTemplate
	) {
		this.redisTemplate = redisTemplate;
	}

	@Override
	public boolean isInitialized(Long memberId) {
		validateMemberId(memberId);
		return Boolean.TRUE.equals(redisTemplate.opsForHash().hasKey(
			RealtimeWalletRedisKey.bettingAvailableBalancesKey(),
			memberId.toString()
		));
	}

	@Override
	public void initializeIfAbsent(
		Long memberId,
		long availableBalance
	) {
		validateMemberId(memberId);
		if (availableBalance < 0) {
			throw new IllegalArgumentException(
				"BETTING wallet balance must not be negative"
			);
		}

		redisTemplate.opsForHash().putIfAbsent(
			RealtimeWalletRedisKey.bettingAvailableBalancesKey(),
			memberId.toString(),
			Long.toString(availableBalance)
		);
	}

	@Override
	public void increaseAvailableBalance(Long memberId, long amount) {
		validateMemberId(memberId);
		if (amount <= 0) {
			throw new IllegalArgumentException(
				"BETTING wallet increase amount must be positive"
			);
		}

		redisTemplate.opsForHash().increment(
			RealtimeWalletRedisKey.bettingAvailableBalancesKey(),
			memberId.toString(),
			amount
		);
	}

	private void validateMemberId(Long memberId) {
		if (memberId == null || memberId <= 0) {
			throw new IllegalArgumentException("memberId must be positive");
		}
	}
}
