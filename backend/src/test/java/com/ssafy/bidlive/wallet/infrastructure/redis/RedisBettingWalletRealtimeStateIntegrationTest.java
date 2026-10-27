package com.ssafy.bidlive.wallet.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.TimeUnit;

import com.ssafy.bidlive.common.support.redis.RealtimeWalletRedisKey;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/** 실제 Redis에서 BETTING 잔액의 최초 적재와 정산 증가를 검증한다. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RedisBettingWalletRealtimeStateIntegrationTest {

	private static final long MEMBER_ID = 25L;

	private Process redisProcess;
	private LettuceConnectionFactory connectionFactory;
	private StringRedisTemplate redisTemplate;
	private RedisBettingWalletRealtimeState realtimeState;

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
		realtimeState = new RedisBettingWalletRealtimeState(redisTemplate);
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
	void initializesAvailableBalance() {
		assertThat(realtimeState.isInitialized(MEMBER_ID)).isFalse();

		realtimeState.initializeIfAbsent(MEMBER_ID, 2_000L);

		assertThat(availableBalance()).isEqualTo("2000");
		assertThat(realtimeState.isInitialized(MEMBER_ID)).isTrue();
	}

	@Test
	void keepsExistingAvailableBalance() {
		redisTemplate.opsForHash().put(
			RealtimeWalletRedisKey.bettingAvailableBalancesKey(),
			Long.toString(MEMBER_ID),
			"1700"
		);

		realtimeState.initializeIfAbsent(MEMBER_ID, 2_000L);

		assertThat(availableBalance()).isEqualTo("1700");
	}

	@Test
	void increasesAvailableBalanceAfterSettlement() {
		realtimeState.initializeIfAbsent(MEMBER_ID, 1_700L);

		realtimeState.increaseAvailableBalance(MEMBER_ID, 1_500L);

		assertThat(availableBalance()).isEqualTo("3200");
	}

	private String availableBalance() {
		Object value = redisTemplate.opsForHash().get(
			RealtimeWalletRedisKey.bettingAvailableBalancesKey(),
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
