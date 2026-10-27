package com.ssafy.bidlive.wallet.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.TimeUnit;

import com.ssafy.bidlive.common.support.redis.RealtimeWalletRedisKey;
import com.ssafy.bidlive.wallet.application.TradeWalletBalanceChangedEvent;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/** 실제 Redis에서 TRADE 지갑 최초 적재와 커밋 변경의 원자 동기화를 검증한다. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RedisTradeWalletRealtimeStateIntegrationTest {

	private static final long MEMBER_ID = 25L;

	private Process redisProcess;
	private LettuceConnectionFactory connectionFactory;
	private StringRedisTemplate redisTemplate;
	private RedisTradeWalletRealtimeState realtimeState;

	@BeforeAll
	void startRedis() throws Exception {
		int port = availablePort();
		redisProcess = new ProcessBuilder(
			"redis-server",
			"--bind", "127.0.0.1",
			"--port", Integer.toString(port),
			"--save", "",
			"--appendonly", "no",
			"--loglevel", "warning"
		)
			.redirectErrorStream(true)
			.redirectOutput(ProcessBuilder.Redirect.DISCARD)
			.start();
		waitUntilListening(port);

		connectionFactory = new LettuceConnectionFactory(
			new RedisStandaloneConfiguration("127.0.0.1", port)
		);
		connectionFactory.afterPropertiesSet();
		connectionFactory.start();

		redisTemplate = new StringRedisTemplate(connectionFactory);
		redisTemplate.afterPropertiesSet();
		realtimeState = new RedisTradeWalletRealtimeState(redisTemplate);
	}

	@AfterEach
	void flushRedis() {
		try (RedisConnection connection = connectionFactory.getConnection()) {
			connection.serverCommands().flushDb();
		}
	}

	@AfterAll
	void stopRedis() throws Exception {
		if (connectionFactory != null) {
			connectionFactory.destroy();
		}
		if (redisProcess != null) {
			redisProcess.destroy();
			if (!redisProcess.waitFor(3, TimeUnit.SECONDS)) {
				redisProcess.destroyForcibly();
				redisProcess.waitFor(3, TimeUnit.SECONDS);
			}
		}
	}

	@Test
	void initializesAvailableAndHeldBalancesTogether() {
		assertThat(realtimeState.isInitialized(MEMBER_ID)).isFalse();

		realtimeState.initializeIfAbsent(
			MEMBER_ID,
			180_000L,
			20_000L
		);

		assertThat(availableBalance()).isEqualTo("180000");
		assertThat(heldBalance()).isEqualTo("20000");
		assertThat(realtimeState.isInitialized(MEMBER_ID)).isTrue();
	}

	@Test
	void keepsExistingRealtimeBalances() {
		putBalances("118000", "82000");
		assertThat(realtimeState.isInitialized(MEMBER_ID)).isTrue();

		realtimeState.initializeIfAbsent(
			MEMBER_ID,
			200_000L,
			0L
		);

		assertThat(availableBalance()).isEqualTo("118000");
		assertThat(heldBalance()).isEqualTo("82000");
	}

	@Test
	void rejectsPartiallyInitializedRealtimeWalletWithoutOverwriting() {
		redisTemplate.opsForHash().put(
			RealtimeWalletRedisKey.tradeAvailableBalancesKey(),
			Long.toString(MEMBER_ID),
			"118000"
		);

		assertThatThrownBy(() -> realtimeState.initializeIfAbsent(
			MEMBER_ID,
			200_000L,
			0L
		))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("partially initialized");
		assertThat(availableBalance()).isEqualTo("118000");
		assertThat(heldBalance()).isNull();
	}

	@Test
	void appliesCommittedBalanceDeltaOnlyOnce() {
		putBalances("150000", "50000");
		TradeWalletBalanceChangedEvent event = change(
			1003L,
			-30_000L,
			0L,
			120_000L,
			50_000L
		);

		realtimeState.synchronizeCommittedChange(event);
		realtimeState.synchronizeCommittedChange(event);

		assertThat(availableBalance()).isEqualTo("120000");
		assertThat(heldBalance()).isEqualTo("50000");
	}

	@Test
	void skipsDeltaWhenStateAlreadyMatchesCommittedSnapshot() {
		putBalances("120000", "50000");

		realtimeState.synchronizeCommittedChange(change(
			1003L,
			-30_000L,
			0L,
			120_000L,
			50_000L
		));

		assertThat(availableBalance()).isEqualTo("120000");
		assertThat(heldBalance()).isEqualTo("50000");
	}

	@Test
	void appliesOutOfOrderCommittedDeltasWithoutLosingEitherChange() {
		putBalances("100000", "100000");

		realtimeState.synchronizeCommittedChange(change(
			1002L,
			20_000L,
			-20_000L,
			170_000L,
			80_000L
		));
		realtimeState.synchronizeCommittedChange(change(
			1001L,
			50_000L,
			0L,
			150_000L,
			100_000L
		));

		assertThat(availableBalance()).isEqualTo("170000");
		assertThat(heldBalance()).isEqualTo("80000");
	}

	@Test
	void restoresMissingStateFromNewestSnapshotAndIgnoresOlderEvent() {
		realtimeState.synchronizeCommittedChange(change(
			1002L,
			20_000L,
			-20_000L,
			170_000L,
			80_000L
		));
		realtimeState.synchronizeCommittedChange(change(
			1001L,
			50_000L,
			0L,
			150_000L,
			100_000L
		));

		assertThat(availableBalance()).isEqualTo("170000");
		assertThat(heldBalance()).isEqualTo("80000");
	}

	@Test
	void rejectsNegativeResultAndRollsBackBothRedisBalances() {
		putBalances("10000", "0");

		assertThatThrownBy(() -> realtimeState.synchronizeCommittedChange(
			change(1004L, -20_000L, 0L, 0L, 0L)
		))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("negative");

		assertThat(availableBalance()).isEqualTo("10000");
		assertThat(heldBalance()).isEqualTo("0");
	}

	@Test
	void rejectsSynchronizationAgainstPartialRealtimeWallet() {
		redisTemplate.opsForHash().put(
			RealtimeWalletRedisKey.tradeAvailableBalancesKey(),
			Long.toString(MEMBER_ID),
			"150000"
		);

		assertThatThrownBy(() -> realtimeState.synchronizeCommittedChange(
			change(1003L, -30_000L, 0L, 120_000L, 50_000L)
		))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("partially initialized");

		assertThat(availableBalance()).isEqualTo("150000");
		assertThat(heldBalance()).isNull();
	}

	private void putBalances(String availableBalance, String heldBalance) {
		redisTemplate.opsForHash().put(
			RealtimeWalletRedisKey.tradeAvailableBalancesKey(),
			Long.toString(MEMBER_ID),
			availableBalance
		);
		redisTemplate.opsForHash().put(
			RealtimeWalletRedisKey.tradeHeldBalancesKey(),
			Long.toString(MEMBER_ID),
			heldBalance
		);
	}

	private TradeWalletBalanceChangedEvent change(
		long ledgerId,
		long availableDelta,
		long heldDelta,
		long availableBalanceAfter,
		long heldBalanceAfter
	) {
		return new TradeWalletBalanceChangedEvent(
			MEMBER_ID,
			ledgerId,
			availableDelta,
			heldDelta,
			availableBalanceAfter,
			heldBalanceAfter
		);
	}

	private String availableBalance() {
		return hashValue(
			RealtimeWalletRedisKey.tradeAvailableBalancesKey()
		);
	}

	private String heldBalance() {
		return hashValue(RealtimeWalletRedisKey.tradeHeldBalancesKey());
	}

	private String hashValue(String key) {
		Object value = redisTemplate.opsForHash().get(
			key,
			Long.toString(MEMBER_ID)
		);
		return value == null ? null : value.toString();
	}

	private static int availablePort() throws IOException {
		try (ServerSocket socket = new ServerSocket(0)) {
			return socket.getLocalPort();
		}
	}

	private void waitUntilListening(int port) throws Exception {
		for (int attempt = 0; attempt < 100; attempt++) {
			if (!redisProcess.isAlive()) {
				throw new IllegalStateException(
					"Redis stopped before accepting connections"
				);
			}
			try (Socket ignored = new Socket("127.0.0.1", port)) {
				return;
			} catch (IOException exception) {
				Thread.sleep(25);
			}
		}
		throw new IllegalStateException(
			"Redis did not start within the expected time"
		);
	}
}
