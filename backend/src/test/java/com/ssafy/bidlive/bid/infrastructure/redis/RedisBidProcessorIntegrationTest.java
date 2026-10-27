package com.ssafy.bidlive.bid.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.ssafy.bidlive.bid.application.BidCommand;
import com.ssafy.bidlive.bid.application.BidProcessingResult;
import com.ssafy.bidlive.bid.domain.BidFailureCode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/** 실제 Redis에서 BID-01의 정상·실패·경계·동시성 원자 처리를 검증한다. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RedisBidProcessorIntegrationTest {

	private static final OffsetDateTime BASE_TIME = OffsetDateTime.parse("2026-07-20T21:12:44+09:00");
	private static final long AUCTION_ID = 100L;
	private static final long SECOND_AUCTION_ID = 200L;

	private Process redisProcess;
	private LettuceConnectionFactory connectionFactory;
	private StringRedisTemplate redisTemplate;
	private RedisBidProcessor processor;

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
		processor = new RedisBidProcessor(redisTemplate);
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
	void rejectsMissingAuction() {
		setAvailableBalance(25L, 100_000L);

		BidProcessingResult result = processor.process(command(25L, 70_000L, "missing", BASE_TIME));

		assertThat(result.failureCode()).isEqualTo(BidFailureCode.AUCTION_NOT_FOUND);
	}

	@Test
	void acceptsFirstAndHigherBidWhileHoldingOnlyTheDifference() {
		initializeAuction("LIVE", BASE_TIME.plusMinutes(10), 50_000L, 999L);
		setAvailableBalance(25L, 100_000L);

		BidProcessingResult first = processor.process(command(25L, 70_000L, "first", BASE_TIME));
		BidProcessingResult higher = processor.process(command(25L, 82_000L, "higher", BASE_TIME.plusSeconds(1)));

		assertThat(first.accepted()).isTrue();
		assertThat(first.previousBidAmount()).isZero();
		assertThat(first.additionalHoldAmount()).isEqualTo(70_000L);
		assertThat(higher.accepted()).isTrue();
		assertThat(higher.previousBidAmount()).isEqualTo(70_000L);
		assertThat(higher.currentBidAmount()).isEqualTo(82_000L);
		assertThat(higher.additionalHoldAmount()).isEqualTo(12_000L);
		assertThat(higher.totalHeldAmount()).isEqualTo(82_000L);
		assertThat(higher.availableBalance()).isEqualTo(18_000L);
		assertThat(higher.submittedAt()).isEqualTo(BASE_TIME.plusSeconds(1));
		assertThat(higher.bidCount()).isEqualTo(2L);
		assertThat(higher.participantCount()).isEqualTo(1L);
		assertThat(hashValue(submittedAtKey(), "25")).isEqualTo(BASE_TIME.plusSeconds(1).toString());
		assertThat(hashValue(participationKey(), "25")).isEqualTo("BID");
		assertThat(hashValue(availableBalanceKey(), "25")).isEqualTo("18000");
		assertThat(hashValue(heldBalanceKey(), "25")).isEqualTo("82000");
		assertThat(hashValue(stateKey(), "version")).isEqualTo("2");
		assertThat(hashValue(processedRequestKey(), "higher")).isEqualTo(BASE_TIME.plusSeconds(1).toString());
	}

	@Test
	void sharesAvailableAndHeldBalancesAcrossAuctions() {
		initializeAuction(AUCTION_ID, "LIVE", BASE_TIME.plusMinutes(10), 50_000L, 999L);
		initializeAuction(SECOND_AUCTION_ID, "LIVE", BASE_TIME.plusMinutes(10), 50_000L, 999L);
		setAvailableBalance(25L, 100_000L);

		BidProcessingResult firstAuction = processor.process(command(
			AUCTION_ID,
			25L,
			60_000L,
			"first-auction",
			BASE_TIME
		));
		BidProcessingResult secondAuction = processor.process(command(
			SECOND_AUCTION_ID,
			25L,
			50_000L,
			"second-auction",
			BASE_TIME.plusSeconds(1)
		));

		assertThat(firstAuction.accepted()).isTrue();
		assertThat(secondAuction.failureCode()).isEqualTo(BidFailureCode.INSUFFICIENT_AVAILABLE_BALANCE);
		assertThat(hashValue(availableBalanceKey(), "25")).isEqualTo("40000");
		assertThat(hashValue(heldBalanceKey(), "25")).isEqualTo("60000");
		assertThat(hashValue(stateKey(AUCTION_ID), "bidCount")).isEqualTo("1");
		assertThat(hashValue(stateKey(SECOND_AUCTION_ID), "bidCount")).isEqualTo("0");
	}

	@Test
	void preventsConcurrentBidsAcrossAuctionsFromReusingAvailableBalance() throws Exception {
		initializeAuction(AUCTION_ID, "LIVE", BASE_TIME.plusMinutes(10), 50_000L, 999L);
		initializeAuction(SECOND_AUCTION_ID, "LIVE", BASE_TIME.plusMinutes(10), 50_000L, 999L);
		setAvailableBalance(25L, 100_000L);

		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		try {
			Future<BidProcessingResult> firstFuture = executor.submit(() -> {
				ready.countDown();
				start.await();
				return processor.process(command(AUCTION_ID, 25L, 70_000L, "concurrent-first", BASE_TIME));
			});
			Future<BidProcessingResult> secondFuture = executor.submit(() -> {
				ready.countDown();
				start.await();
				return processor.process(command(
					SECOND_AUCTION_ID,
					25L,
					70_000L,
					"concurrent-second",
					BASE_TIME
				));
			});

			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
			start.countDown();

			List<BidProcessingResult> results = List.of(
				firstFuture.get(5, TimeUnit.SECONDS),
				secondFuture.get(5, TimeUnit.SECONDS)
			);
			assertThat(results).filteredOn(BidProcessingResult::accepted).hasSize(1);
			assertThat(results)
				.filteredOn(result -> !result.accepted())
				.extracting(BidProcessingResult::failureCode)
				.containsExactly(BidFailureCode.INSUFFICIENT_AVAILABLE_BALANCE);
		} finally {
			executor.shutdownNow();
		}

		assertThat(hashValue(availableBalanceKey(), "25")).isEqualTo("30000");
		assertThat(hashValue(heldBalanceKey(), "25")).isEqualTo("70000");
		long acceptedBidCount = Long.parseLong(hashValue(stateKey(AUCTION_ID), "bidCount"))
			+ Long.parseLong(hashValue(stateKey(SECOND_AUCTION_ID), "bidCount"));
		assertThat(acceptedBidCount).isEqualTo(1L);
	}

	@Test
	void rejectsEqualAndLowerBidsWithoutMutatingAcceptedState() {
		initializeAuction("LIVE", BASE_TIME.plusMinutes(10), 50_000L, 999L);
		setAvailableBalance(25L, 100_000L);
		processor.process(command(25L, 70_000L, "accepted", BASE_TIME));

		BidProcessingResult equal = processor.process(command(25L, 70_000L, "equal", BASE_TIME.plusSeconds(1)));
		BidProcessingResult lower = processor.process(command(25L, 69_000L, "lower", BASE_TIME.plusSeconds(2)));

		assertThat(equal.failureCode()).isEqualTo(BidFailureCode.BID_NOT_HIGHER_THAN_PREVIOUS);
		assertThat(lower.failureCode()).isEqualTo(BidFailureCode.BID_NOT_HIGHER_THAN_PREVIOUS);
		assertThat(hashValue(memberBidKey(), "25")).isEqualTo("70000");
		assertThat(hashValue(stateKey(), "bidCount")).isEqualTo("1");
		assertThat(hashValue(availableBalanceKey(), "25")).isEqualTo("30000");
	}

	@Test
	void acceptsConcurrentBidsFromMultipleUsersAtomically() throws Exception {
		int userCount = 16;
		initializeAuction("LIVE", BASE_TIME.plusMinutes(10), 50_000L, 999L);
		for (long memberId = 1; memberId <= userCount; memberId++) {
			setAvailableBalance(memberId, 100_000L);
		}

		ExecutorService executor = Executors.newFixedThreadPool(userCount);
		CountDownLatch ready = new CountDownLatch(userCount);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<BidProcessingResult>> futures = new ArrayList<>();
		try {
			for (long memberId = 1; memberId <= userCount; memberId++) {
				long bidderId = memberId;
				futures.add(executor.submit(() -> {
					ready.countDown();
					start.await();
					return processor.process(command(
						bidderId,
						60_000L + bidderId,
						"concurrent-" + bidderId,
						BASE_TIME
					));
				}));
			}
			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
			start.countDown();

			for (Future<BidProcessingResult> future : futures) {
				assertThat(future.get(5, TimeUnit.SECONDS).accepted()).isTrue();
			}
		} finally {
			executor.shutdownNow();
		}

		assertThat(hashValue(stateKey(), "bidCount")).isEqualTo(Integer.toString(userCount));
		assertThat(hashValue(stateKey(), "participantCount")).isEqualTo(Integer.toString(userCount));
		assertThat(hashValue(stateKey(), "version")).isEqualTo(Integer.toString(userCount));
		assertThat(redisTemplate.opsForHash().size(memberBidKey())).isEqualTo(userCount);
	}

	@Test
	void rejectsAtDeadlineAndRecognizesDuplicateRequest() {
		initializeAuction("LIVE", BASE_TIME, 50_000L, 999L);
		setAvailableBalance(25L, 100_000L);

		BidProcessingResult ended = processor.process(command(25L, 70_000L, "at-deadline", BASE_TIME));
		assertThat(ended.failureCode()).isEqualTo(BidFailureCode.AUCTION_ALREADY_ENDED);

		redisTemplate.opsForHash().put(stateKey(), "endAt", Long.toString(BASE_TIME.plusMinutes(1).toInstant().toEpochMilli()));
		BidCommand duplicateCommand = command(25L, 70_000L, "same-request", BASE_TIME);
		assertThat(processor.process(duplicateCommand).accepted()).isTrue();
		assertThat(processor.process(duplicateCommand).failureCode()).isEqualTo(BidFailureCode.DUPLICATE_REQUEST);
		assertThat(hashValue(stateKey(), "bidCount")).isEqualTo("1");
	}

	@Test
	void rejectsEveryRemainingSpecifiedBusinessFailure() {
		initializeAuction("READY", BASE_TIME.plusMinutes(10), 50_000L, 999L);
		setAvailableBalance(25L, 100_000L);
		assertThat(processor.process(command(25L, 70_000L, "not-live", BASE_TIME)).failureCode())
			.isEqualTo(BidFailureCode.AUCTION_NOT_LIVE);

		redisTemplate.opsForHash().put(stateKey(), "status", "LIVE");
		assertThat(processor.process(command(25L, 49_999L, "below-reserve", BASE_TIME)).failureCode())
			.isEqualTo(BidFailureCode.BID_BELOW_RESERVE_PRICE);

		redisTemplate.opsForHash().put(stateKey(), "sellerId", "25");
		assertThat(processor.process(command(25L, 70_000L, "seller", BASE_TIME)).failureCode())
			.isEqualTo(BidFailureCode.SELLER_CANNOT_BID);

		redisTemplate.opsForHash().put(stateKey(), "sellerId", "999");
		redisTemplate.opsForHash().put(participationKey(), "25", "PREDICTION");
		assertThat(processor.process(command(25L, 70_000L, "prediction", BASE_TIME)).failureCode())
			.isEqualTo(BidFailureCode.PREDICTION_ALREADY_JOINED);

		redisTemplate.opsForHash().put(participationKey(), "25", "NONE");
		redisTemplate.opsForHash().put(availableBalanceKey(), "25", "69999");
		assertThat(processor.process(command(25L, 70_000L, "insufficient", BASE_TIME)).failureCode())
			.isEqualTo(BidFailureCode.INSUFFICIENT_AVAILABLE_BALANCE);

		assertThat(hashValue(stateKey(), "bidCount")).isEqualTo("0");
		assertThat(redisTemplate.opsForHash().size(memberBidKey())).isZero();
	}

	private void initializeAuction(String status, OffsetDateTime endAt, long reservePrice, long sellerId) {
		initializeAuction(AUCTION_ID, status, endAt, reservePrice, sellerId);
	}

	private void initializeAuction(
		long auctionId,
		String status,
		OffsetDateTime endAt,
		long reservePrice,
		long sellerId
	) {
		Map<String, String> state = new HashMap<>();
		state.put("status", status);
		state.put("endAt", Long.toString(endAt.toInstant().toEpochMilli()));
		state.put("reservePrice", Long.toString(reservePrice));
		state.put("sellerId", Long.toString(sellerId));
		state.put("bidCount", "0");
		state.put("participantCount", "0");
		state.put("version", "0");
		redisTemplate.opsForHash().putAll(stateKey(auctionId), state);
	}

	private void setAvailableBalance(long memberId, long amount) {
		redisTemplate.opsForHash().put(availableBalanceKey(), Long.toString(memberId), Long.toString(amount));
	}

	private BidCommand command(long memberId, long amount, String requestId, OffsetDateTime requestedAt) {
		return command(AUCTION_ID, memberId, amount, requestId, requestedAt);
	}

	private BidCommand command(
		long auctionId,
		long memberId,
		long amount,
		String requestId,
		OffsetDateTime requestedAt
	) {
		return new BidCommand(auctionId, memberId, amount, requestId, requestedAt);
	}

	private String hashValue(String key, String hashKey) {
		return (String) redisTemplate.opsForHash().get(key, hashKey);
	}

	private String stateKey() {
		return stateKey(AUCTION_ID);
	}

	private String stateKey(long auctionId) {
		return keys(auctionId).get(0);
	}

	private String memberBidKey() {
		return keys().get(1);
	}

	private String submittedAtKey() {
		return keys().get(2);
	}

	private String participationKey() {
		return keys().get(3);
	}

	private String availableBalanceKey() {
		return keys().get(4);
	}

	private String heldBalanceKey() {
		return keys().get(5);
	}

	private String processedRequestKey() {
		return keys().get(6);
	}

	private List<String> keys() {
		return keys(AUCTION_ID);
	}

	private List<String> keys(long auctionId) {
		return BidRedisKey.processingKeys(auctionId);
	}

	private static int availablePort() throws IOException {
		try (ServerSocket socket = new ServerSocket(0)) {
			return socket.getLocalPort();
		}
	}

	private void waitUntilListening(int port) throws Exception {
		for (int attempt = 0; attempt < 100; attempt++) {
			if (!redisProcess.isAlive()) {
				throw new IllegalStateException("Redis process stopped before accepting connections");
			}
			try (Socket ignored = new Socket("127.0.0.1", port)) {
				return;
			} catch (IOException exception) {
				Thread.sleep(25);
			}
		}
		throw new IllegalStateException("Redis did not start within the expected time");
	}
}
