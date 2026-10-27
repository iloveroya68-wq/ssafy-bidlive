package com.ssafy.bidlive.wallet.infrastructure.redis;

import java.util.List;

import com.ssafy.bidlive.common.support.redis.RealtimeWalletRedisKey;
import com.ssafy.bidlive.wallet.application.TradeWalletBalanceChangedEvent;
import com.ssafy.bidlive.wallet.application.TradeWalletRealtimeState;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

/** Redis TRADE 잔액의 최초 적재와 커밋 변경을 원자적으로 처리한다. */
@Repository
public class RedisTradeWalletRealtimeState
	implements TradeWalletRealtimeState {

	private static final long ALREADY_INITIALIZED = 0L;
	private static final long INITIALIZED = 1L;
	private static final long NOT_INITIALIZED = 2L;
	private static final long PARTIAL_STATE = -1L;
	private static final long NEGATIVE_BALANCE = -2L;
	private static final long SYNCHRONIZED_LEDGER_TTL_SECONDS = 86_400L;
	private static final DefaultRedisScript<Long> INITIALIZE_SCRIPT =
		createScript("redis/lua/trade-wallet-initialize.lua");
	private static final DefaultRedisScript<Long> SYNCHRONIZE_SCRIPT =
		createScript("redis/lua/trade-wallet-synchronize.lua");

	private final StringRedisTemplate redisTemplate;

	public RedisTradeWalletRealtimeState(
		StringRedisTemplate redisTemplate
	) {
		this.redisTemplate = redisTemplate;
	}

	@Override
	public boolean isInitialized(Long memberId) {
		validateMemberId(memberId);
		Long result = execute(memberId.toString());
		if (result == ALREADY_INITIALIZED) {
			return true;
		}
		if (result == NOT_INITIALIZED) {
			return false;
		}
		validateCommonResult(result);
		throw unknownResult(result);
	}

	@Override
	public void initializeIfAbsent(
		Long memberId,
		long availableBalance,
		long heldBalance
	) {
		validate(memberId, availableBalance, heldBalance);

		Long result = execute(
			memberId.toString(),
			Long.toString(availableBalance),
			Long.toString(heldBalance)
		);
		validateCommonResult(result);
		if (result != INITIALIZED && result != ALREADY_INITIALIZED) {
			throw unknownResult(result);
		}
	}

	@Override
	public void synchronizeCommittedChange(
		TradeWalletBalanceChangedEvent event
	) {
		validate(event);
		Long result = redisTemplate.execute(
			SYNCHRONIZE_SCRIPT,
			List.of(
				RealtimeWalletRedisKey.tradeAvailableBalancesKey(),
				RealtimeWalletRedisKey.tradeHeldBalancesKey(),
				RealtimeWalletRedisKey
					.tradeSynchronizationWatermarksKey(),
				RealtimeWalletRedisKey.tradeSynchronizedLedgerKey(
					event.ledgerId()
				)
			),
			event.memberId().toString(),
			event.ledgerId().toString(),
			Long.toString(event.availableDelta()),
			Long.toString(event.heldDelta()),
			Long.toString(event.availableBalanceAfter()),
			Long.toString(event.heldBalanceAfter()),
			Long.toString(SYNCHRONIZED_LEDGER_TTL_SECONDS)
		);
		validateCommonResult(result);
		if (result == NEGATIVE_BALANCE) {
			throw new IllegalStateException(
				"Redis TRADE wallet synchronization would make "
					+ "a balance negative"
			);
		}
		if (result != ALREADY_INITIALIZED
			&& result != INITIALIZED
			&& result != NOT_INITIALIZED) {
			throw unknownResult(result);
		}
	}

	private void validate(
		Long memberId,
		long availableBalance,
		long heldBalance
	) {
		validateMemberId(memberId);
		if (availableBalance < 0 || heldBalance < 0) {
			throw new IllegalArgumentException(
				"TRADE wallet balances must not be negative"
			);
		}
	}

	private void validateMemberId(Long memberId) {
		if (memberId == null || memberId <= 0) {
			throw new IllegalArgumentException("memberId must be positive");
		}
	}

	private void validate(TradeWalletBalanceChangedEvent event) {
		if (event == null) {
			throw new IllegalArgumentException(
				"TRADE wallet change event must not be null"
			);
		}
		validateMemberId(event.memberId());
		if (event.ledgerId() == null || event.ledgerId() <= 0) {
			throw new IllegalArgumentException("ledgerId must be positive");
		}
		if (event.availableBalanceAfter() < 0
			|| event.heldBalanceAfter() < 0) {
			throw new IllegalArgumentException(
				"TRADE wallet balances must not be negative"
			);
		}
	}

	private Long execute(String... arguments) {
		return redisTemplate.execute(
			INITIALIZE_SCRIPT,
			List.of(
				RealtimeWalletRedisKey.tradeAvailableBalancesKey(),
				RealtimeWalletRedisKey.tradeHeldBalancesKey()
			),
			(Object[]) arguments
		);
	}

	private void validateCommonResult(Long result) {
		if (result == null) {
			throw new IllegalStateException(
				"Redis returned no TRADE wallet initialization result"
			);
		}
		if (result == PARTIAL_STATE) {
			throw new IllegalStateException(
				"Redis TRADE wallet balances are partially initialized"
			);
		}
	}

	private IllegalStateException unknownResult(Long result) {
		return new IllegalStateException(
			"Redis returned an unknown TRADE wallet initialization result: "
				+ result
		);
	}

	private static DefaultRedisScript<Long> createScript(String location) {
		DefaultRedisScript<Long> script = new DefaultRedisScript<>();
		script.setLocation(new ClassPathResource(location));
		script.setResultType(Long.class);
		return script;
	}
}
