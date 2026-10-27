package com.ssafy.bidlive.chat.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/** 실제 Redis에서 채팅 mute TTL 조회·만료·경매·회원 격리를 검증한다. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RedisChatMuteReaderIntegrationTest {

	private static final Instant NOW =
		Instant.parse("2026-07-31T04:00:00Z");

	private Process redisProcess;
	private LettuceConnectionFactory connectionFactory;
	private StringRedisTemplate redisTemplate;
	private RedisChatMuteReader reader;

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
		reader = new RedisChatMuteReader(
			redisTemplate,
			Clock.fixed(NOW, ZoneOffset.UTC)
		);
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
	void returnsMutedUntilFromRemainingRedisTtl() {
		Duration muteDuration = Duration.ofSeconds(2);
		redisTemplate.opsForValue().set(
			ChatRedisKey.mute(100L, 25L),
			"1",
			muteDuration
		);

		Instant mutedUntil = reader.findMutedUntil(100L, 25L)
			.orElseThrow();
		Duration remaining = Duration.between(NOW, mutedUntil);

		assertThat(remaining.toMillis())
			.isBetween(1L, muteDuration.toMillis());
	}

	@Test
	void returnsEmptyForMissingAndExpiredMuteKey()
		throws InterruptedException {
		assertThat(reader.findMutedUntil(100L, 25L)).isEmpty();

		redisTemplate.opsForValue().set(
			ChatRedisKey.mute(100L, 25L),
			"1",
			Duration.ofMillis(100)
		);
		Thread.sleep(250);

		assertThat(reader.findMutedUntil(100L, 25L)).isEmpty();
	}

	@Test
	void isolatesMuteByAuctionAndMember() {
		redisTemplate.opsForValue().set(
			ChatRedisKey.mute(100L, 25L),
			"1",
			Duration.ofSeconds(2)
		);

		assertThat(reader.findMutedUntil(100L, 25L)).isPresent();
		assertThat(reader.findMutedUntil(101L, 25L)).isEmpty();
		assertThat(reader.findMutedUntil(100L, 26L)).isEmpty();
	}

	@Test
	void rejectsMuteKeyWithoutTtl() {
		redisTemplate.opsForValue().set(
			ChatRedisKey.mute(100L, 25L),
			"1"
		);

		assertThatThrownBy(() -> reader.findMutedUntil(100L, 25L))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("invalid chat mute Redis TTL: -1");
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
