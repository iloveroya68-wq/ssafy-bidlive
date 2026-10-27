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

import com.ssafy.bidlive.auction.application.AuctionSettlementClaimCommand;
import com.ssafy.bidlive.auction.application.AuctionSettlementClaimResult;
import com.ssafy.bidlive.auction.application.AuctionSettlementCompletionCommand;
import com.ssafy.bidlive.auction.application.AuctionSettlementCompletionResult;
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

/** 실제 Redis에서 BID-07의 마감 검증·원자 선점과 동시 단일 성공을 검증한다. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RedisAuctionSettlementClaimProcessorIntegrationTest {

	private static final long AUCTION_ID = 100L;
	private static final Instant END_AT =
		Instant.parse("2026-07-20T12:20:00Z");

	private Process redisProcess;
	private LettuceConnectionFactory connectionFactory;
	private StringRedisTemplate redisTemplate;
	private RedisAuctionSettlementClaimProcessor processor;
	private RedisAuctionSettlementCompletionProcessor completionProcessor;

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
		processor = new RedisAuctionSettlementClaimProcessor(redisTemplate);
		completionProcessor =
			new RedisAuctionSettlementCompletionProcessor(redisTemplate);
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
	void claimsAtExactDeadlineAndUpdatesStateMetadata() {
		initializeAuction("LIVE", END_AT, 7);

		AuctionSettlementClaimResult result = processor.claim(
			command(END_AT)
		);

		assertThat(result).isEqualTo(AuctionSettlementClaimResult.CLAIMED);
		assertThat(hashValue("status")).isEqualTo("SETTLING");
		assertThat(hashValue("endAt"))
			.isEqualTo(Long.toString(END_AT.toEpochMilli()));
		assertThat(hashValue("version")).isEqualTo("8");
		assertThat(hashValue("updatedAt")).isEqualTo(END_AT.toString());
	}

	@Test
	void completesSettlingStateAndKeepsAlreadyEndedIdempotent() {
		initializeAuction("LIVE", END_AT, 7);
		assertThat(processor.claim(command(END_AT)))
			.isEqualTo(AuctionSettlementClaimResult.CLAIMED);

		AuctionSettlementCompletionCommand completion =
			new AuctionSettlementCompletionCommand(
				AUCTION_ID,
				END_AT.plusSeconds(1)
			);
		assertThat(completionProcessor.complete(completion))
			.isEqualTo(AuctionSettlementCompletionResult.COMPLETED);
		assertThat(hashValue("status")).isEqualTo("ENDED");
		assertThat(hashValue("version")).isEqualTo("9");
		assertThat(hashValue("updatedAt"))
			.isEqualTo(END_AT.plusSeconds(1).toString());
		assertThat(completionProcessor.complete(completion))
			.isEqualTo(AuctionSettlementCompletionResult.ALREADY_ENDED);
	}

	@Test
	void rejectsCompletionForMissingOrNonSettlingState() {
		AuctionSettlementCompletionCommand completion =
			new AuctionSettlementCompletionCommand(AUCTION_ID, END_AT);
		assertThat(completionProcessor.complete(completion))
			.isEqualTo(
				AuctionSettlementCompletionResult.AUCTION_NOT_FOUND
			);

		initializeAuction("LIVE", END_AT, 7);
		assertThat(completionProcessor.complete(completion))
			.isEqualTo(
				AuctionSettlementCompletionResult.AUCTION_NOT_SETTLING
			);
	}

	@Test
	void rejectsKnownNonClaimableStatesWithoutMutation() {
		assertThat(processor.claim(command(END_AT)))
			.isEqualTo(AuctionSettlementClaimResult.AUCTION_NOT_FOUND);

		initializeAuction("WAITING", END_AT, 7);
		assertThat(processor.claim(command(END_AT)))
			.isEqualTo(AuctionSettlementClaimResult.AUCTION_NOT_LIVE);

		put("status", "LIVE");
		assertThat(processor.claim(command(END_AT.minusMillis(1))))
			.isEqualTo(AuctionSettlementClaimResult.AUCTION_NOT_DUE);
		assertThat(hashValue("status")).isEqualTo("LIVE");
		assertThat(hashValue("version")).isEqualTo("7");

		put("status", "SETTLING");
		assertThat(processor.claim(command(END_AT)))
			.isEqualTo(AuctionSettlementClaimResult.ALREADY_SETTLING);

		put("status", "ENDED");
		assertThat(processor.claim(command(END_AT)))
			.isEqualTo(AuctionSettlementClaimResult.ALREADY_ENDED);
	}

	@Test
	void recognizesStatusOnlyEndedHashFromWaitingForceEnd() {
		put("status", "ENDED");

		assertThat(processor.claim(command(END_AT)))
			.isEqualTo(AuctionSettlementClaimResult.ALREADY_ENDED);
	}

	@Test
	void rejectsCorruptedLiveStateAsInfrastructureFailure() {
		initializeAuction("LIVE", END_AT, 7);
		redisTemplate.opsForHash().delete(stateKey(), "version");

		assertThatThrownBy(() -> processor.claim(command(END_AT)))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("INVALID_AUCTION_STATE");

		put("version", "7");
		put("status", "UNKNOWN");
		assertThatThrownBy(() -> processor.claim(command(END_AT)))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("INVALID_AUCTION_STATE");
	}

	@Test
	void allowsOnlyOneConcurrentSettlementClaim() throws Exception {
		int requestCount = 12;
		initializeAuction("LIVE", END_AT, 7);
		ExecutorService executor = Executors.newFixedThreadPool(requestCount);
		CountDownLatch ready = new CountDownLatch(requestCount);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<AuctionSettlementClaimResult>> futures =
			new ArrayList<>();

		try {
			for (int index = 0; index < requestCount; index++) {
				futures.add(executor.submit(() -> {
					ready.countDown();
					start.await();
					return processor.claim(command(END_AT));
				}));
			}
			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
			start.countDown();

			List<AuctionSettlementClaimResult> results = new ArrayList<>();
			for (Future<AuctionSettlementClaimResult> future : futures) {
				results.add(future.get(5, TimeUnit.SECONDS));
			}
			assertThat(results)
				.filteredOn(AuctionSettlementClaimResult::claimed)
				.hasSize(1);
			assertThat(results)
				.filteredOn(result -> !result.claimed())
				.containsOnly(AuctionSettlementClaimResult.ALREADY_SETTLING);
		} finally {
			executor.shutdownNow();
		}

		assertThat(hashValue("status")).isEqualTo("SETTLING");
		assertThat(hashValue("version")).isEqualTo("8");
	}

	private void initializeAuction(
		String status,
		Instant endAt,
		long version
	) {
		Map<String, String> state = new LinkedHashMap<>();
		state.put("status", status);
		state.put("endAt", Long.toString(endAt.toEpochMilli()));
		state.put("version", Long.toString(version));
		state.put("updatedAt", endAt.minusSeconds(1).toString());
		redisTemplate.opsForHash().putAll(stateKey(), state);
	}

	private AuctionSettlementClaimCommand command(Instant requestedAt) {
		return new AuctionSettlementClaimCommand(AUCTION_ID, requestedAt);
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
