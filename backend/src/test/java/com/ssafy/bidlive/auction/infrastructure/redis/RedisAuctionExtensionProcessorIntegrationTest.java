package com.ssafy.bidlive.auction.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.ssafy.bidlive.auction.application.AuctionExtensionCommand;
import com.ssafy.bidlive.auction.application.AuctionExtensionProcessingResult;
import com.ssafy.bidlive.auction.domain.AuctionExtensionErrorCode;
import com.ssafy.bidlive.common.support.redis.RealtimeAuctionRedisKey;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/** 실제 Redis에서 BID-03의 원자 갱신, 경계 판정과 동시 최대 횟수를 검증한다. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RedisAuctionExtensionProcessorIntegrationTest {

	private static final long AUCTION_ID = 100L;
	private static final long SELLER_ID = 25L;
	private static final Instant NOW =
		Instant.parse("2026-07-20T12:20:00Z");

	private Process redisProcess;
	private LettuceConnectionFactory connectionFactory;
	private StringRedisTemplate redisTemplate;
	private RedisAuctionExtensionProcessor processor;

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
		processor = new RedisAuctionExtensionProcessor(redisTemplate);
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
	void atomicallyExtendsAtExactlyOneMinute() {
		Instant previousEndAt = NOW.plusSeconds(60);
		initializeAuction("LIVE", previousEndAt, SELLER_ID, 1, 7);

		AuctionExtensionProcessingResult result = processor.extend(command());

		assertThat(result.accepted()).isTrue();
		assertThat(result.previousEndAt()).isEqualTo(previousEndAt);
		assertThat(result.endAt()).isEqualTo(previousEndAt.plusSeconds(300));
		assertThat(result.extensionCount()).isEqualTo(2);
		assertThat(result.remainingExtensions()).isEqualTo(1);
		assertThat(result.version()).isEqualTo(8);
		assertThat(hashValue("endAt")).isEqualTo(Long.toString(
			previousEndAt.plusSeconds(300).toEpochMilli()
		));
		assertThat(hashValue("extensionCount")).isEqualTo("2");
		assertThat(hashValue("version")).isEqualTo("8");
	}

	@Test
	void rejectsSpecifiedFailuresWithoutMutatingState() {
		assertThat(processor.extend(command()).failureCode()).isEqualTo(
			AuctionExtensionErrorCode.AUCTION_NOT_FOUND
		);

		initializeAuction("ENDED", NOW.plusSeconds(60), SELLER_ID, 0, 0);
		assertThat(processor.extend(command()).failureCode()).isEqualTo(
			AuctionExtensionErrorCode.AUCTION_NOT_LIVE
		);

		put("status", "LIVE");
		put("sellerId", "999");
		assertThat(processor.extend(command()).failureCode()).isEqualTo(
			AuctionExtensionErrorCode.NOT_AUCTION_SELLER
		);

		put("sellerId", Long.toString(SELLER_ID));
		put("extensionCount", "3");
		assertThat(processor.extend(command()).failureCode()).isEqualTo(
			AuctionExtensionErrorCode.EXTENSION_LIMIT_EXCEEDED
		);

		put("extensionCount", "0");
		put("endAt", Long.toString(NOW.plusMillis(59_999).toEpochMilli()));
		assertThat(processor.extend(command()).failureCode()).isEqualTo(
			AuctionExtensionErrorCode.EXTENSION_TOO_LATE
		);
		assertThat(hashValue("endAt")).isEqualTo(Long.toString(
			NOW.plusMillis(59_999).toEpochMilli()
		));
		assertThat(hashValue("extensionCount")).isEqualTo("0");
		assertThat(hashValue("version")).isEqualTo("0");
	}

	@Test
	void rejectsCorruptedStateAsInfrastructureFailure() {
		initializeAuction("LIVE", NOW.plusSeconds(60), SELLER_ID, 0, 0);
		redisTemplate.opsForHash().delete(stateKey(), "version");

		assertThatThrownBy(() -> processor.extend(command()))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("INVALID_AUCTION_STATE");

		initializeAuction("LIVE", NOW.plusSeconds(60), SELLER_ID, -1, 0);
		assertThatThrownBy(() -> processor.extend(command()))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("INVALID_AUCTION_STATE");
		assertThat(hashValue("extensionCount")).isEqualTo("-1");
		assertThat(hashValue("version")).isEqualTo("0");
	}

	@Test
	void allowsOnlyThreeConcurrentExtensions() throws Exception {
		int requestCount = 12;
		Instant initialEndAt = NOW.plusSeconds(60);
		initializeAuction("LIVE", initialEndAt, SELLER_ID, 0, 0);
		ExecutorService executor = Executors.newFixedThreadPool(requestCount);
		CountDownLatch ready = new CountDownLatch(requestCount);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<AuctionExtensionProcessingResult>> futures =
			new ArrayList<>();

		try {
			for (int index = 0; index < requestCount; index++) {
				futures.add(executor.submit(() -> {
					ready.countDown();
					start.await();
					return processor.extend(command());
				}));
			}
			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
			start.countDown();

			List<AuctionExtensionProcessingResult> results = new ArrayList<>();
			for (Future<AuctionExtensionProcessingResult> future : futures) {
				results.add(future.get(5, TimeUnit.SECONDS));
			}
			assertThat(results)
				.filteredOn(AuctionExtensionProcessingResult::accepted)
				.hasSize(3);
			assertThat(results)
				.filteredOn(result -> !result.accepted())
				.extracting(AuctionExtensionProcessingResult::failureCode)
				.containsOnly(
					AuctionExtensionErrorCode.EXTENSION_LIMIT_EXCEEDED
				);
		} finally {
			executor.shutdownNow();
		}

		assertThat(hashValue("endAt")).isEqualTo(Long.toString(
			initialEndAt.plusSeconds(900).toEpochMilli()
		));
		assertThat(hashValue("extensionCount")).isEqualTo("3");
		assertThat(hashValue("version")).isEqualTo("3");
	}

	private void initializeAuction(
		String status,
		Instant endAt,
		long sellerId,
		int extensionCount,
		long version
	) {
		Map<String, String> state = new LinkedHashMap<>();
		state.put("status", status);
		state.put("endAt", Long.toString(endAt.toEpochMilli()));
		state.put("sellerId", Long.toString(sellerId));
		state.put("extensionCount", Integer.toString(extensionCount));
		state.put("version", Long.toString(version));
		redisTemplate.opsForHash().putAll(stateKey(), state);
	}

	private AuctionExtensionCommand command() {
		return new AuctionExtensionCommand(AUCTION_ID, SELLER_ID, NOW);
	}

	private void put(String field, String value) {
		redisTemplate.opsForHash().put(stateKey(), field, value);
	}

	private String hashValue(String field) {
		return (String) redisTemplate.opsForHash().get(stateKey(), field);
	}

	private String stateKey() {
		return RealtimeAuctionRedisKey.auctionStateKey(AUCTION_ID);
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
