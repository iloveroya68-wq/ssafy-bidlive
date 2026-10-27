package com.ssafy.bidlive.chat.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.ssafy.bidlive.chat.application.ChatSlowModeResult;
import com.ssafy.bidlive.chat.infrastructure.ChatSlowModeProperties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/** 실제 Redis에서 슬로우 모드 TTL·격리·동시 요청 원자성을 검증한다. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RedisChatSlowModeCheckerIntegrationTest {

	private static final Instant REQUESTED_AT =
		Instant.parse("2026-07-31T04:00:00Z");
	private static final Duration STANDARD_INTERVAL =
		Duration.ofSeconds(2);

	private Process redisProcess;
	private LettuceConnectionFactory connectionFactory;
	private StringRedisTemplate redisTemplate;
	private RedisChatSlowModeChecker checker;

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
		checker = checkerWithInterval(STANDARD_INTERVAL);
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
	void permitsFirstRequestAndLimitsImmediateRetryWithTtl() {
		ChatSlowModeResult first = checker.checkAndConsume(
			100L,
			25L,
			REQUESTED_AT
		);
		ChatSlowModeResult retry = checker.checkAndConsume(
			100L,
			25L,
			REQUESTED_AT
		);

		assertThat(first).isEqualTo(ChatSlowModeResult.permit());
		assertThat(retry.allowed()).isFalse();
		assertThat(retry.retryAfterSeconds()).isBetween(1L, 2L);
		assertThat(redisTemplate.opsForValue().get(
			"bidlive:chat:{100}:slow-mode:25"
		)).isEqualTo("1");
		assertThat(redisTemplate.getExpire(
			"bidlive:chat:{100}:slow-mode:25",
			TimeUnit.MILLISECONDS
		)).isBetween(1L, STANDARD_INTERVAL.toMillis());
	}

	@Test
	void permitsAgainAfterRedisTtlExpires() throws InterruptedException {
		RedisChatSlowModeChecker shortIntervalChecker =
			checkerWithInterval(Duration.ofMillis(100));
		assertThat(shortIntervalChecker.checkAndConsume(
			100L,
			25L,
			REQUESTED_AT
		).allowed()).isTrue();

		Thread.sleep(250);

		assertThat(shortIntervalChecker.checkAndConsume(
			100L,
			25L,
			REQUESTED_AT.plusSeconds(1)
		).allowed()).isTrue();
	}

	@Test
	void isolatesCountersByAuctionAndMember() {
		assertThat(checker.checkAndConsume(
			100L,
			25L,
			REQUESTED_AT
		).allowed()).isTrue();

		assertThat(checker.checkAndConsume(
			101L,
			25L,
			REQUESTED_AT
		).allowed()).isTrue();
		assertThat(checker.checkAndConsume(
			100L,
			26L,
			REQUESTED_AT
		).allowed()).isTrue();
		assertThat(checker.checkAndConsume(
			100L,
			25L,
			REQUESTED_AT
		).allowed()).isFalse();
	}

	@Test
	void permitsExactlyOneConcurrentRequestForSameMember()
		throws Exception {
		warmUpLuaScript();
		int requestCount = 16;
		ExecutorService executor =
			Executors.newFixedThreadPool(requestCount);
		CountDownLatch ready = new CountDownLatch(requestCount);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<ChatSlowModeResult>> futures = new ArrayList<>();
		try {
			for (int index = 0; index < requestCount; index++) {
				futures.add(executor.submit(() -> {
					ready.countDown();
					start.await();
					return checker.checkAndConsume(
						100L,
						25L,
						REQUESTED_AT
					);
				}));
			}
			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
			start.countDown();

			List<ChatSlowModeResult> results = new ArrayList<>();
			long completionDeadline = System.nanoTime()
				+ TimeUnit.SECONDS.toNanos(10);
			for (Future<ChatSlowModeResult> future : futures) {
				long remainingNanos = completionDeadline
					- System.nanoTime();
				results.add(future.get(
					Math.max(1L, remainingNanos),
					TimeUnit.NANOSECONDS
				));
			}
			assertThat(results)
				.filteredOn(ChatSlowModeResult::allowed)
				.hasSize(1);
			assertThat(results)
				.filteredOn(result -> !result.allowed())
				.hasSize(requestCount - 1);
		} finally {
			executor.shutdownNow();
		}
	}

	private void warmUpLuaScript() {
		checker.checkAndConsume(999L, 999L, REQUESTED_AT);
		redisTemplate.delete(ChatRedisKey.slowMode(999L, 999L));
	}

	private RedisChatSlowModeChecker checkerWithInterval(
		Duration interval
	) {
		ChatSlowModeProperties properties = new ChatSlowModeProperties();
		properties.setInterval(interval);
		return new RedisChatSlowModeChecker(redisTemplate, properties);
	}

	private static int availablePort() throws IOException {
		try (ServerSocket socket = new ServerSocket(0)) {
			return socket.getLocalPort();
		}
	}

	private void waitUntilListening(int port) throws Exception {
		long deadline = System.nanoTime()
			+ TimeUnit.SECONDS.toNanos(5);
		while (System.nanoTime() < deadline) {
			if (!redisProcess.isAlive()) {
				throw new IllegalStateException(
					"Redis process stopped before accepting connections"
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
