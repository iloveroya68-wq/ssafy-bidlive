package com.ssafy.bidlive.prediction.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.bidlive.common.event.WsEvent;
import com.ssafy.bidlive.common.security.JwtTokenProvider;
import com.ssafy.bidlive.common.support.redis.RealtimeAuctionRedisKey;
import com.ssafy.bidlive.media.application.ViewerSessionTrackingService;
import com.ssafy.bidlive.member.domain.Member;
import com.ssafy.bidlive.member.domain.MemberRole;
import com.ssafy.bidlive.prediction.api.dto.PredictionAcceptedData;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/** 실제 PRED-01 요청의 저장·동시성과 신규 참여 브로드캐스트를 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
class PredictionCreateSuccessE2eTest {

	private static final long PREDICTED_PRICE = 74_000L;
	private static final long STAKE_AMOUNT = 300L;
	private static final long INITIAL_BALANCE = 2_000L;
	private static final RedisProcess REDIS = startRedis();

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private JwtTokenProvider jwtTokenProvider;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private StringRedisTemplate redisTemplate;

	@MockitoBean
	private ViewerSessionTrackingService viewerSessionTrackingService;

	@MockitoBean
	private SimpMessagingTemplate messagingTemplate;

	@DynamicPropertySource
	static void redisProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.data.redis.host", () -> "127.0.0.1");
		registry.add("spring.data.redis.port", REDIS::port);
	}

	@AfterAll
	static void stopRedis() throws Exception {
		REDIS.process().destroy();
		if (!REDIS.process().waitFor(3, TimeUnit.SECONDS)) {
			REDIS.process().destroyForcibly();
			REDIS.process().waitFor(3, TimeUnit.SECONDS);
		}
	}

	@Test
	void createsPredictionWithoutExposingOtherPredictionDataAndStoresSideEffects()
		throws Exception {
		Fixture fixture = createFixture();
		UUID clientRequestId = UUID.randomUUID();
		List<String> redisKeys = initializeAuctionRedis(fixture, clientRequestId);

		MvcResult mvcResult = performPrediction(
			fixture,
			clientRequestId,
			PREDICTED_PRICE,
			bearerAccessToken(fixture.predictorId())
		)
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.auctionId").value(fixture.auctionId()))
			.andExpect(jsonPath("$.predictedPrice").value(PREDICTED_PRICE))
			.andExpect(jsonPath("$.stakeAmount").value(STAKE_AMOUNT))
			.andExpect(jsonPath("$.status").value("PLACED"))
			.andExpect(jsonPath("$.submittedAt").isNotEmpty())
			.andReturn();

		JsonNode response = objectMapper.readTree(
			mvcResult.getResponse().getContentAsByteArray()
		);
		assertOnlyPredictionResponseFields(response);
		long predictionId = response.path("predictionId").asLong();

		assertThat(predictionId).isPositive();
		assertThat(prediction(predictionId)).containsExactly(
			fixture.auctionId(),
			fixture.predictorId(),
			PREDICTED_PRICE,
			STAKE_AMOUNT,
			clientRequestId.toString()
		);
		assertThat(participantCount(fixture.auctionId())).isEqualTo(1);
		assertThat(walletBalance(fixture.walletId()))
			.isEqualTo(INITIAL_BALANCE - STAKE_AMOUNT);
		assertThat(ledger(predictionId)).containsExactly(
			"BET",
			-STAKE_AMOUNT,
			0L,
			INITIAL_BALANCE - STAKE_AMOUNT,
			0L,
			predictionId
		);

		assertThat(redisValue(redisKeys.get(1), fixture.predictorId().toString()))
			.isEqualTo("PREDICTION");
		assertThat(redisValue(redisKeys.get(0), "participantCount"))
			.isEqualTo("1");
		assertThat(redisValue(redisKeys.get(0), "version")).isEqualTo("1");
		assertThat(redisValue(redisKeys.get(3), fixture.predictorId().toString()))
			.isEqualTo(Long.toString(INITIAL_BALANCE - STAKE_AMOUNT));
		assertThat(redisValue(redisKeys.get(2), "fingerprint")).isEqualTo(
			"%d:%d:%d:%d".formatted(
				fixture.predictorId(),
				fixture.auctionId(),
				PREDICTED_PRICE,
				STAKE_AMOUNT
			)
		);
		assertPredictionAcceptedBroadcast(fixture.auctionId(), 1L);
	}

	@Test
	void returnsOriginalPredictionAndKeepsSingleSideEffectsForDuplicateRequest()
		throws Exception {
		Fixture fixture = createFixture();
		UUID clientRequestId = UUID.randomUUID();
		List<String> redisKeys = initializeAuctionRedis(fixture, clientRequestId);
		String accessToken = bearerAccessToken(fixture.predictorId());

		MvcResult firstResult = performPrediction(
			fixture,
			clientRequestId,
			PREDICTED_PRICE,
			accessToken
		)
			.andExpect(status().isCreated())
			.andReturn();
		MvcResult duplicateResult = performPrediction(
			fixture,
			clientRequestId,
			PREDICTED_PRICE,
			accessToken
		)
			.andExpect(status().isCreated())
			.andReturn();

		JsonNode firstResponse = response(firstResult);
		JsonNode duplicateResponse = response(duplicateResult);

		for (String field : List.of(
			"predictionId",
			"auctionId",
			"predictedPrice",
			"stakeAmount",
			"status"
		)) {
			assertThat(duplicateResponse.path(field))
				.isEqualTo(firstResponse.path(field));
		}
		assertSubmittedAtIsSame(firstResponse, duplicateResponse);
		assertOnlyPredictionResponseFields(duplicateResponse);
		assertSingleStoredPrediction(fixture);
		assertThat(redisValue(redisKeys.get(0), "participantCount"))
			.isEqualTo("1");
		assertThat(redisValue(redisKeys.get(0), "version")).isEqualTo("1");
		assertThat(redisValue(redisKeys.get(3), fixture.predictorId().toString()))
			.isEqualTo(Long.toString(INITIAL_BALANCE - STAKE_AMOUNT));
		assertPredictionAcceptedBroadcast(fixture.auctionId(), 1L);
	}

	@Test
	void acceptsOnlyOneConcurrentPredictionFromSameMember() throws Exception {
		Fixture fixture = createFixture();
		UUID firstRequestId = UUID.randomUUID();
		UUID secondRequestId = UUID.randomUUID();
		List<String> redisKeys = initializeAuctionRedis(fixture, firstRequestId);
		String accessToken = bearerAccessToken(fixture.predictorId());
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		List<MvcResult> results;

		try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
			Future<MvcResult> first = executor.submit(() ->
				performPredictionAfterStart(
					ready,
					start,
					fixture,
					firstRequestId,
					PREDICTED_PRICE,
					accessToken
				)
			);
			Future<MvcResult> second = executor.submit(() ->
				performPredictionAfterStart(
					ready,
					start,
					fixture,
					secondRequestId,
					PREDICTED_PRICE + 1_000L,
					accessToken
				)
			);

			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
			start.countDown();
			results = List.of(
				first.get(10, TimeUnit.SECONDS),
				second.get(10, TimeUnit.SECONDS)
			);
		}

		assertThat(results)
			.extracting(result -> result.getResponse().getStatus())
			.containsExactlyInAnyOrder(
				HttpStatus.CREATED.value(),
				HttpStatus.CONFLICT.value()
			);

		MvcResult createdResult = resultWithStatus(
			results,
			HttpStatus.CREATED
		);
		MvcResult rejectedResult = resultWithStatus(
			results,
			HttpStatus.CONFLICT
		);
		assertOnlyPredictionResponseFields(response(createdResult));
		assertThat(response(rejectedResult).path("code").asText())
			.isEqualTo("PREDICTION_ALREADY_EXISTS");

		assertSingleStoredPrediction(fixture);
		assertThat(redisValue(redisKeys.get(0), "participantCount"))
			.isEqualTo("1");
		assertThat(redisValue(redisKeys.get(0), "version")).isEqualTo("1");
		assertThat(redisValue(redisKeys.get(3), fixture.predictorId().toString()))
			.isEqualTo(Long.toString(INITIAL_BALANCE - STAKE_AMOUNT));
		assertPredictionAcceptedBroadcast(fixture.auctionId(), 1L);
	}

	private void assertPredictionAcceptedBroadcast(
		Long auctionId,
		long participantCount
	) {
		ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
		verify(messagingTemplate, times(1)).convertAndSend(
			eq("/topic/auctions/%d".formatted(auctionId)),
			payload.capture()
		);

		assertThat(payload.getValue()).isInstanceOf(WsEvent.class);
		WsEvent<?> event = (WsEvent<?>) payload.getValue();
		assertThat(event.type()).isEqualTo("PREDICTION_ACCEPTED");
		assertThat(event.data()).isEqualTo(new PredictionAcceptedData(
			participantCount,
			"누군가 예측에 참여했습니다!"
		));
	}

	private ResultActions performPrediction(
		Fixture fixture,
		UUID clientRequestId,
		long predictedPrice,
		String accessToken
	) throws Exception {
		return mockMvc.perform(post(
				"/api/v1/auctions/{auctionId}/predictions",
				fixture.auctionId()
			)
			.header(HttpHeaders.AUTHORIZATION, accessToken)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
				{
				  "predictedPrice": %d,
				  "stakeAmount": %d,
				  "clientRequestId": "%s"
				}
				""".formatted(
					predictedPrice,
					STAKE_AMOUNT,
					clientRequestId
				)));
	}

	private MvcResult performPredictionAfterStart(
		CountDownLatch ready,
		CountDownLatch start,
		Fixture fixture,
		UUID clientRequestId,
		long predictedPrice,
		String accessToken
	) throws Exception {
		ready.countDown();
		start.await();
		return performPrediction(
			fixture,
			clientRequestId,
			predictedPrice,
			accessToken
		).andReturn();
	}

	private List<String> initializeAuctionRedis(
		Fixture fixture,
		UUID clientRequestId
	) {
		List<String> keys =
			RealtimeAuctionRedisKey.predictionParticipationKeys(
				fixture.auctionId(),
				clientRequestId
			);
		OffsetDateTime now = OffsetDateTime.now();
		Map<String, String> state = new HashMap<>();
		state.put("status", "LIVE");
		state.put(
			"endAt",
			Long.toString(now.plusMinutes(10).toInstant().toEpochMilli())
		);
		state.put("sellerId", fixture.sellerId().toString());
		state.put("participantCount", "0");
		state.put("version", "0");
		state.put("updatedAt", now.minusSeconds(1).toString());
		redisTemplate.opsForHash().putAll(keys.get(0), state);
		return keys;
	}

	private JsonNode response(MvcResult result) throws IOException {
		return objectMapper.readTree(
			result.getResponse().getContentAsByteArray()
		);
	}

	private MvcResult resultWithStatus(
		List<MvcResult> results,
		HttpStatus status
	) {
		return results.stream()
			.filter(result -> result.getResponse().getStatus() == status.value())
			.findFirst()
			.orElseThrow();
	}

	private void assertOnlyPredictionResponseFields(JsonNode response) {
		List<String> fieldNames = new ArrayList<>();
		response.fieldNames().forEachRemaining(fieldNames::add);

		assertThat(fieldNames).containsExactlyInAnyOrder(
			"predictionId",
			"auctionId",
			"predictedPrice",
			"stakeAmount",
			"status",
			"submittedAt"
		);
	}

	private void assertSingleStoredPrediction(Fixture fixture) {
		assertThat(participantCount(fixture.auctionId())).isEqualTo(1);
		assertThat(predictionCount(fixture.auctionId())).isEqualTo(1);
		assertThat(betLedgerCount(fixture.auctionId())).isEqualTo(1);
		assertThat(walletBalance(fixture.walletId()))
			.isEqualTo(INITIAL_BALANCE - STAKE_AMOUNT);
	}

	private void assertSubmittedAtIsSame(
		JsonNode firstResponse,
		JsonNode duplicateResponse
	) {
		OffsetDateTime first = OffsetDateTime.parse(
			firstResponse.path("submittedAt").asText()
		);
		OffsetDateTime duplicate = OffsetDateTime.parse(
			duplicateResponse.path("submittedAt").asText()
		);
		assertThat(Duration.between(first, duplicate).abs())
			.isLessThan(Duration.ofNanos(1_000));
	}

	private Fixture createFixture() {
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		Long sellerId = insertMember("pred01-e2e-seller-" + suffix);
		Long predictorId = insertMember("pred01-e2e-predictor-" + suffix);
		Long walletId = insert("""
			INSERT INTO wallet (
				member_id,
				wallet_type,
				available_balance,
				held_balance
			)
			VALUES (?, 'BETTING', ?, 0)
			RETURNING wallet_id
			""", predictorId, INITIAL_BALANCE);
		Long categoryId = insert("""
			INSERT INTO product_category (code, name)
			VALUES (?, ?)
			RETURNING category_id
			""", "PRED01_E2E_" + suffix, "PRED-01 E2E " + suffix);
		Long productId = insert("""
			INSERT INTO product (
				seller_member_id,
				category_id,
				title,
				description
			)
			VALUES (?, ?, 'Prediction E2E product', 'PRED-01 success E2E')
			RETURNING product_id
			""", sellerId, categoryId);
		Long auctionId = insert("""
			INSERT INTO auction_room (
				product_id,
				reserve_price,
				duration_minutes,
				start_type,
				status
			)
			VALUES (?, 1000, 10, 'IMMEDIATE', 'LIVE')
			RETURNING auction_room_id
			""", productId);
		return new Fixture(auctionId, sellerId, predictorId, walletId);
	}

	private Long insertMember(String nickname) {
		return insert("""
			INSERT INTO member (nickname, normalized_nickname)
			VALUES (?, ?)
			RETURNING member_id
			""", nickname, nickname);
	}

	private Long insert(String sql, Object... arguments) {
		return jdbcTemplate.queryForObject(sql, Long.class, arguments);
	}

	private String bearerAccessToken(Long memberId) {
		Member member = mock(Member.class);
		when(member.getId()).thenReturn(memberId);
		when(member.getRole()).thenReturn(MemberRole.USER);
		return "Bearer " + jwtTokenProvider.issue(member).accessToken();
	}

	private Object redisValue(String key, String field) {
		return redisTemplate.opsForHash().get(key, field);
	}

	private int participantCount(Long auctionId) {
		Integer count = jdbcTemplate.queryForObject("""
			SELECT COUNT(*)
			FROM auction_participant
			WHERE auction_room_id = ?
				AND participation_type = 'PREDICTOR'
			""", Integer.class, auctionId);
		return count == null ? 0 : count;
	}

	private int predictionCount(Long auctionId) {
		Integer count = jdbcTemplate.queryForObject("""
			SELECT COUNT(*)
			FROM prediction
			JOIN auction_participant participant
				ON participant.auction_participant_id =
					prediction.auction_participant_id
			WHERE participant.auction_room_id = ?
			""", Integer.class, auctionId);
		return count == null ? 0 : count;
	}

	private int betLedgerCount(Long auctionId) {
		Integer count = jdbcTemplate.queryForObject("""
			SELECT COUNT(*)
			FROM betting_point_ledger ledger
			JOIN prediction
				ON prediction.prediction_id = ledger.prediction_id
			JOIN auction_participant participant
				ON participant.auction_participant_id =
					prediction.auction_participant_id
			WHERE participant.auction_room_id = ?
			""", Integer.class, auctionId);
		return count == null ? 0 : count;
	}

	private long walletBalance(Long walletId) {
		Long balance = jdbcTemplate.queryForObject("""
			SELECT available_balance
			FROM wallet
			WHERE wallet_id = ?
			""", Long.class, walletId);
		return balance == null ? 0L : balance;
	}

	private Object[] prediction(long predictionId) {
		return jdbcTemplate.queryForObject("""
			SELECT
				participant.auction_room_id,
				participant.member_id,
				prediction.predicted_price,
				prediction.stake_amount,
				prediction.idempotency_key
			FROM prediction
			JOIN auction_participant participant
				ON participant.auction_participant_id =
					prediction.auction_participant_id
			WHERE prediction.prediction_id = ?
			""", (resultSet, rowNumber) -> new Object[] {
				resultSet.getLong("auction_room_id"),
				resultSet.getLong("member_id"),
				resultSet.getLong("predicted_price"),
				resultSet.getLong("stake_amount"),
				resultSet.getString("idempotency_key")
			}, predictionId);
	}

	private Object[] ledger(long predictionId) {
		return jdbcTemplate.queryForObject("""
			SELECT
				transaction_type,
				available_delta,
				held_delta,
				available_balance_after,
				held_balance_after,
				prediction_id
			FROM betting_point_ledger
			WHERE prediction_id = ?
			""", (resultSet, rowNumber) -> new Object[] {
				resultSet.getString("transaction_type"),
				resultSet.getLong("available_delta"),
				resultSet.getLong("held_delta"),
				resultSet.getLong("available_balance_after"),
				resultSet.getLong("held_balance_after"),
				resultSet.getLong("prediction_id")
			}, predictionId);
	}

	private static RedisProcess startRedis() {
		try {
			int port = availablePort();
			Process process = new ProcessBuilder(
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
			return new RedisProcess(process, port);
		} catch (Exception exception) {
			throw new IllegalStateException(
				"Redis test server did not start",
				exception
			);
		}
	}

	private static int availablePort() throws IOException {
		try (ServerSocket socket = new ServerSocket(0)) {
			return socket.getLocalPort();
		}
	}

	private static void waitUntilListening(int port) throws Exception {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
		while (System.nanoTime() < deadline) {
			try (Socket ignored = new Socket("127.0.0.1", port)) {
				return;
			} catch (IOException exception) {
				Thread.sleep(25);
			}
		}
		throw new IllegalStateException("Redis test server did not start");
	}

	private record Fixture(
		Long auctionId,
		Long sellerId,
		Long predictorId,
		Long walletId
	) {
	}

	private record RedisProcess(Process process, int port) {
	}
}
