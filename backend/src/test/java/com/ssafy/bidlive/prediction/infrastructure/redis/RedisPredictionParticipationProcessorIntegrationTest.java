package com.ssafy.bidlive.prediction.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.ssafy.bidlive.common.support.redis.RealtimeAuctionRedisKey;
import com.ssafy.bidlive.prediction.application.PredictionParticipationCommand;
import com.ssafy.bidlive.prediction.application.PredictionParticipationResult;
import com.ssafy.bidlive.prediction.domain.PredictionParticipationFailureCode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/** 실제 Redis에서 예측 참여·멱등·BETTING 포인트 차감을 원자 검증한다. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RedisPredictionParticipationProcessorIntegrationTest {

	private static final long AUCTION_ID = 100L;
	private static final OffsetDateTime BASE_TIME =
		OffsetDateTime.parse("2026-07-29T20:59:00+09:00");
	private static final UUID CLIENT_REQUEST_ID =
		UUID.fromString("2b8bb687-6d5e-4e09-b651-b88727266a51");
	private static final UUID SECOND_CLIENT_REQUEST_ID =
		UUID.fromString("3c9cc798-7e6f-4f1a-c762-c99838377b62");

	private Process redisProcess;
	private LettuceConnectionFactory connectionFactory;
	private StringRedisTemplate redisTemplate;
	private RedisPredictionParticipationProcessor processor;

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
		processor = new RedisPredictionParticipationProcessor(redisTemplate);
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
	void rejectsMissingAuctionWithoutCreatingParticipation() {
		PredictionParticipationResult result =
			processor.process(command(25L, BASE_TIME));

		assertThat(result.failureCode())
			.isEqualTo(PredictionParticipationFailureCode.AUCTION_NOT_FOUND);
		assertThat(participation(25L)).isNull();
	}

	@ParameterizedTest
	@ValueSource(strings = {"WAITING", "ENDED"})
	void rejectsAuctionThatIsNotLive(String status) {
		initializeAuction(status, BASE_TIME.plusMinutes(1), 999L);

		PredictionParticipationResult result =
			processor.process(command(25L, BASE_TIME));

		assertThat(result.failureCode())
			.isEqualTo(PredictionParticipationFailureCode.AUCTION_NOT_LIVE);
		assertUnchanged();
	}

	@Test
	void rejectsAtAndAfterDeadline() {
		OffsetDateTime endsAt = BASE_TIME.plusMinutes(1);
		initializeAuction("LIVE", endsAt, 999L);

		PredictionParticipationResult atDeadline =
			processor.process(command(25L, endsAt));
		PredictionParticipationResult afterDeadline =
			processor.process(command(26L, endsAt.plusNanos(1)));

		assertThat(atDeadline.failureCode())
			.isEqualTo(PredictionParticipationFailureCode.PREDICTION_CLOSED);
		assertThat(afterDeadline.failureCode())
			.isEqualTo(PredictionParticipationFailureCode.PREDICTION_CLOSED);
		assertUnchanged();
	}

	@Test
	void rejectsSellerBeforeCreatingParticipation() {
		initializeAuction("LIVE", BASE_TIME.plusMinutes(1), 25L);

		PredictionParticipationResult result =
			processor.process(command(25L, BASE_TIME));

		assertThat(result.failureCode())
			.isEqualTo(
				PredictionParticipationFailureCode.SELLER_CANNOT_PREDICT
			);
		assertUnchanged();
	}

	@Test
	void rejectsBidderAndExistingPredictor() {
		initializeAuction("LIVE", BASE_TIME.plusMinutes(1), 999L);
		setParticipation(25L, "BID");
		setParticipation(26L, "PREDICTION");

		PredictionParticipationResult bidder =
			processor.process(command(25L, BASE_TIME));
		PredictionParticipationResult predictor =
			processor.process(command(26L, BASE_TIME));

		assertThat(bidder.failureCode())
			.isEqualTo(
				PredictionParticipationFailureCode.BIDDER_CANNOT_PREDICT
			);
		assertThat(predictor.failureCode())
			.isEqualTo(
				PredictionParticipationFailureCode
					.PREDICTION_ALREADY_EXISTS
			);
		assertThat(stateValue("participantCount")).isEqualTo("7");
		assertThat(stateValue("version")).isEqualTo("3");
	}

	@Test
	void rejectsWhenBettingPointsAreInsufficient() {
		initializeAuction("LIVE", BASE_TIME.plusMinutes(1), 999L);
		setBettingBalance(25L, 299L);

		PredictionParticipationResult result =
			processor.process(command(25L, BASE_TIME));

		assertThat(result.failureCode())
			.isEqualTo(
				PredictionParticipationFailureCode
					.INSUFFICIENT_BETTING_POINTS
			);
		assertThat(participation(25L)).isNull();
		assertThat(stateValue("participantCount")).isEqualTo("7");
		assertThat(stateValue("version")).isEqualTo("3");
		assertThat(bettingBalance(25L)).isEqualTo("299");
	}

	@Test
	void registersPredictionParticipationBeforeDeadline() {
		initializeAuction(
			"LIVE",
			BASE_TIME.plusNanos(1_000_000),
			999L
		);

		PredictionParticipationResult result =
			processor.process(
				command(25L, BASE_TIME, CLIENT_REQUEST_ID)
			);

		assertThat(result.accepted()).isTrue();
		assertThat(result.submittedAt()).isEqualTo(BASE_TIME);
		assertThat(result.participantCount()).isEqualTo(8L);
		assertThat(result.availableBalance()).isEqualTo(700L);
		assertThat(result.duplicated()).isFalse();
		assertThat(
			redisTemplate.opsForHash().get(
				requestKey(CLIENT_REQUEST_ID),
				"fingerprint"
			)
		).isEqualTo("25:100:74000:300");
		assertThat(participation(25L)).isEqualTo("PREDICTION");
		assertThat(stateValue("participantCount")).isEqualTo("8");
		assertThat(stateValue("version")).isEqualTo("4");
		assertThat(stateValue("updatedAt")).isEqualTo(BASE_TIME.toString());
		assertThat(bettingBalance(25L)).isEqualTo("700");
	}

	@Test
	void returnsOriginalSuccessForSameRequestRetry() {
		initializeAuction("LIVE", BASE_TIME.plusMinutes(1), 999L);
		PredictionParticipationCommand original =
			command(25L, BASE_TIME, CLIENT_REQUEST_ID);

		PredictionParticipationResult first =
			processor.process(original);
		redisTemplate.opsForHash().put(stateKey(), "status", "ENDED");
		PredictionParticipationResult retry = processor.process(
			command(
				25L,
				BASE_TIME.plusMinutes(2),
				CLIENT_REQUEST_ID
			)
		);

		assertThat(first.accepted()).isTrue();
		assertThat(first.duplicated()).isFalse();
		assertThat(retry.accepted()).isTrue();
		assertThat(retry.duplicated()).isTrue();
		assertThat(retry.submittedAt()).isEqualTo(BASE_TIME);
		assertThat(retry.participantCount()).isEqualTo(8L);
		assertThat(retry.availableBalance()).isEqualTo(700L);
		assertThat(stateValue("participantCount")).isEqualTo("8");
		assertThat(stateValue("version")).isEqualTo("4");
		assertThat(bettingBalance(25L)).isEqualTo("700");
	}

	@Test
	void rejectsClientRequestIdReusedWithDifferentContent() {
		initializeAuction("LIVE", BASE_TIME.plusMinutes(1), 999L);
		processor.process(
			command(
				25L,
				BASE_TIME,
				CLIENT_REQUEST_ID,
				74_000L,
				300L
			)
		);

		PredictionParticipationResult reused = processor.process(
			command(
				25L,
				BASE_TIME,
				CLIENT_REQUEST_ID,
				75_000L,
				300L
			)
		);

		assertThat(reused.accepted()).isFalse();
		assertThat(reused.failureCode())
			.isEqualTo(
				PredictionParticipationFailureCode
					.IDEMPOTENCY_KEY_REUSED
			);
		assertThat(stateValue("participantCount")).isEqualTo("8");
		assertThat(stateValue("version")).isEqualTo("4");
		assertThat(bettingBalance(25L)).isEqualTo("700");
	}

	@Test
	void processesConcurrentSameRequestOnlyOnce() throws Exception {
		initializeAuction("LIVE", BASE_TIME.plusMinutes(1), 999L);
		CountDownLatch start = new CountDownLatch(1);
		PredictionParticipationCommand command =
			command(25L, BASE_TIME, CLIENT_REQUEST_ID);
		try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
			Future<PredictionParticipationResult> first = executor.submit(
				() -> processAfterStart(start, command)
			);
			Future<PredictionParticipationResult> second = executor.submit(
				() -> processAfterStart(start, command)
			);

			start.countDown();
			List<PredictionParticipationResult> results =
				List.of(first.get(), second.get());

			assertThat(results)
				.allSatisfy(result ->
					assertThat(result.accepted()).isTrue()
				);
			assertThat(results)
				.filteredOn(PredictionParticipationResult::duplicated)
				.hasSize(1);
			assertThat(stateValue("participantCount")).isEqualTo("8");
			assertThat(stateValue("version")).isEqualTo("4");
			assertThat(bettingBalance(25L)).isEqualTo("700");
		}
	}

	@Test
	void acceptsOnlyOneOfConcurrentRequestsFromSameMember() throws Exception {
		initializeAuction("LIVE", BASE_TIME.plusMinutes(1), 999L);
		CountDownLatch start = new CountDownLatch(1);
		try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
			Future<PredictionParticipationResult> first = executor.submit(
				() -> processAfterStart(
					start,
					command(25L, BASE_TIME, CLIENT_REQUEST_ID)
				)
			);
			Future<PredictionParticipationResult> second = executor.submit(
				() -> processAfterStart(
					start,
					command(25L, BASE_TIME, SECOND_CLIENT_REQUEST_ID)
				)
			);

			start.countDown();
			List<PredictionParticipationResult> results =
				List.of(first.get(), second.get());

			assertThat(results)
				.filteredOn(PredictionParticipationResult::accepted)
				.hasSize(1);
			assertThat(results)
				.filteredOn(result ->
					result.failureCode()
						== PredictionParticipationFailureCode
							.PREDICTION_ALREADY_EXISTS
				)
				.hasSize(1);
			assertThat(stateValue("participantCount")).isEqualTo("8");
			assertThat(stateValue("version")).isEqualTo("4");
			assertThat(bettingBalance(25L)).isEqualTo("700");
		}
	}

	@Test
	void rejectsMalformedLiveAuctionStateWithoutMutation() {
		initializeAuction("LIVE", BASE_TIME.plusMinutes(1), 999L);
		redisTemplate.opsForHash().delete(stateKey(), "endAt");

		assertThatThrownBy(() ->
			processor.process(command(25L, BASE_TIME))
		)
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining(
				"invalid prediction participation result"
			);
		assertUnchanged();
	}

	private PredictionParticipationResult processAfterStart(
		CountDownLatch start,
		PredictionParticipationCommand command
	) throws InterruptedException {
		start.await();
		return processor.process(command);
	}

	private void initializeAuction(
		String status,
		OffsetDateTime endsAt,
		long sellerId
	) {
		Map<String, String> state = new HashMap<>();
		state.put("status", status);
		state.put(
			"endAt",
			Long.toString(endsAt.toInstant().toEpochMilli())
		);
		state.put("sellerId", Long.toString(sellerId));
		state.put("participantCount", "7");
		state.put("version", "3");
		state.put("updatedAt", BASE_TIME.minusMinutes(1).toString());
		redisTemplate.opsForHash().putAll(stateKey(), state);
		setBettingBalance(25L, 1_000L);
		setBettingBalance(26L, 1_000L);
	}

	private PredictionParticipationCommand command(
		long memberId,
		OffsetDateTime requestedAt
	) {
		return command(
			memberId,
			requestedAt,
			new UUID(0L, memberId)
		);
	}

	private PredictionParticipationCommand command(
		long memberId,
		OffsetDateTime requestedAt,
		UUID clientRequestId
	) {
		return command(
			memberId,
			requestedAt,
			clientRequestId,
			74_000L,
			300L
		);
	}

	private PredictionParticipationCommand command(
		long memberId,
		OffsetDateTime requestedAt,
		UUID clientRequestId,
		long predictedPrice,
		long stakeAmount
	) {
		return new PredictionParticipationCommand(
			AUCTION_ID,
			memberId,
			predictedPrice,
			stakeAmount,
			clientRequestId,
			requestedAt
		);
	}

	private void setBettingBalance(long memberId, long balance) {
		redisTemplate.opsForHash().put(
			bettingBalanceKey(),
			Long.toString(memberId),
			Long.toString(balance)
		);
	}

	private Object bettingBalance(long memberId) {
		return redisTemplate.opsForHash().get(
			bettingBalanceKey(),
			Long.toString(memberId)
		);
	}

	private void setParticipation(long memberId, String participation) {
		redisTemplate.opsForHash().put(
			participationKey(),
			Long.toString(memberId),
			participation
		);
	}

	private Object participation(long memberId) {
		return redisTemplate.opsForHash().get(
			participationKey(),
			Long.toString(memberId)
		);
	}

	private Object stateValue(String field) {
		return redisTemplate.opsForHash().get(stateKey(), field);
	}

	private void assertUnchanged() {
		assertThat(participation(25L)).isNull();
		assertThat(stateValue("participantCount")).isEqualTo("7");
		assertThat(stateValue("version")).isEqualTo("3");
		assertThat(stateValue("updatedAt"))
			.isEqualTo(BASE_TIME.minusMinutes(1).toString());
		assertThat(bettingBalance(25L)).isEqualTo("1000");
	}

	private String stateKey() {
		return RealtimeAuctionRedisKey
			.predictionParticipationKeys(
				AUCTION_ID,
				CLIENT_REQUEST_ID
			)
			.get(0);
	}

	private String participationKey() {
		return RealtimeAuctionRedisKey
			.predictionParticipationKeys(
				AUCTION_ID,
				CLIENT_REQUEST_ID
			)
			.get(1);
	}

	private String bettingBalanceKey() {
		return RealtimeAuctionRedisKey
			.predictionParticipationKeys(
				AUCTION_ID,
				CLIENT_REQUEST_ID
			)
			.get(3);
	}

	private String requestKey(UUID clientRequestId) {
		return RealtimeAuctionRedisKey
			.predictionParticipationKeys(AUCTION_ID, clientRequestId)
			.get(2);
	}

	private int availablePort() throws IOException {
		try (ServerSocket socket = new ServerSocket(0)) {
			return socket.getLocalPort();
		}
	}

	private void waitUntilListening(int port) throws Exception {
		long deadline = System.nanoTime()
			+ TimeUnit.SECONDS.toNanos(5);
		while (System.nanoTime() < deadline) {
			try (Socket ignored = new Socket("127.0.0.1", port)) {
				return;
			} catch (IOException exception) {
				Thread.sleep(25);
			}
		}
		throw new IllegalStateException("Redis test server did not start");
	}
}
