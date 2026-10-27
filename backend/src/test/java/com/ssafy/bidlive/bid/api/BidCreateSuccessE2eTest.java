package com.ssafy.bidlive.bid.api;

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
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.bidlive.bid.api.dto.BidAcceptedData;
import com.ssafy.bidlive.common.event.WsEvent;
import com.ssafy.bidlive.common.security.JwtTokenProvider;
import com.ssafy.bidlive.common.support.redis.RealtimeAuctionRedisKey;
import com.ssafy.bidlive.media.application.ViewerSessionTrackingService;
import com.ssafy.bidlive.member.domain.Member;
import com.ssafy.bidlive.member.domain.MemberRole;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/** 실제 BID-06 입찰이 Redis·DB 반영 후 공개 이벤트까지 이어지는지 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
class BidCreateSuccessE2eTest {

	private static final long INITIAL_BALANCE = 200_000L;
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
	void storesFirstAndHigherBidOnceAcrossRedisAndPostgres() throws Exception {
		Fixture fixture = createFixture();
		List<String> redisKeys = initializeRedis(fixture);
		assertThat(redisValue(
			redisKeys.get(4),
			fixture.bidderId().toString()
		)).isNull();
		assertThat(redisValue(
			redisKeys.get(5),
			fixture.bidderId().toString()
		)).isNull();
		UUID firstRequestId = UUID.randomUUID();
		UUID secondRequestId = UUID.randomUUID();
		String accessToken = bearerAccessToken(fixture.bidderId());

		performBid(fixture, firstRequestId, 70_000L, accessToken)
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.previousBidAmount").value(0L))
			.andExpect(jsonPath("$.additionalHoldAmount").value(70_000L));
		MvcResult second = performBid(
			fixture,
			secondRequestId,
			82_000L,
			accessToken
		)
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.auctionId").value(fixture.auctionId()))
			.andExpect(jsonPath("$.myBidAmount").value(82_000L))
			.andExpect(jsonPath("$.previousBidAmount").value(70_000L))
			.andExpect(jsonPath("$.additionalHoldAmount").value(12_000L))
			.andExpect(jsonPath("$.totalHeldAmount").value(82_000L))
			.andExpect(jsonPath("$.availableBalance").value(118_000L))
			.andReturn();
		MvcResult duplicated = performBid(
			fixture,
			secondRequestId,
			82_000L,
			accessToken
		)
			.andExpect(status().isCreated())
			.andReturn();

		JsonNode secondResponse = response(second);
		JsonNode duplicatedResponse = response(duplicated);
		assertOnlyBidResponseFields(secondResponse);
		assertOnlyBidResponseFields(duplicatedResponse);
		assertThat(duplicatedResponse.path("submittedAt"))
			.isEqualTo(secondResponse.path("submittedAt"));

		assertThat(participantCount(fixture.auctionId())).isEqualTo(1);
		assertThat(bidRows(fixture.auctionId())).containsExactly(
			new BidRow(1, 70_000L, firstRequestId.toString()),
			new BidRow(2, 82_000L, secondRequestId.toString())
		);
		assertThat(walletBalances(fixture.tradeWalletId()))
			.containsExactly(118_000L, 82_000L);
		assertThat(ledgerDeltas(fixture.tradeWalletId())).containsExactly(
			new LedgerDelta(-70_000L, 70_000L),
			new LedgerDelta(-12_000L, 12_000L)
		);

		assertThat(redisValue(redisKeys.get(1), fixture.bidderId().toString()))
			.isEqualTo("82000");
		assertThat(redisValue(redisKeys.get(3), fixture.bidderId().toString()))
			.isEqualTo("BID");
		assertThat(redisValue(redisKeys.get(4), fixture.bidderId().toString()))
			.isEqualTo("118000");
		assertThat(redisValue(redisKeys.get(5), fixture.bidderId().toString()))
			.isEqualTo("82000");
		assertThat(redisValue(redisKeys.get(0), "bidCount")).isEqualTo("2");
		assertThat(redisValue(redisKeys.get(0), "participantCount"))
			.isEqualTo("1");
		assertBidAcceptedBroadcasts(fixture.auctionId());
	}

	private void assertBidAcceptedBroadcasts(Long auctionId) {
		ArgumentCaptor<Object> payloads = ArgumentCaptor.forClass(Object.class);
		verify(messagingTemplate, times(2)).convertAndSend(
			eq("/topic/auctions/%d".formatted(auctionId)),
			payloads.capture()
		);

		List<Object> messages = payloads.getAllValues();
		assertThat(messages).hasSize(2);
		assertBidAccepted(messages.get(0), 1L, 1L);
		assertBidAccepted(messages.get(1), 2L, 1L);
	}

	private void assertBidAccepted(
		Object payload,
		long bidCount,
		long participantCount
	) {
		assertThat(payload).isInstanceOf(WsEvent.class);
		WsEvent<?> event = (WsEvent<?>) payload;
		assertThat(event.type()).isEqualTo("BID_ACCEPTED");
		assertThat(event.data()).isEqualTo(new BidAcceptedData(
			bidCount,
			participantCount,
			"누군가 베팅을 진행 중입니다!"
		));
	}

	private ResultActions performBid(
		Fixture fixture,
		UUID requestId,
		long amount,
		String accessToken
	) throws Exception {
		return mockMvc.perform(post(
				"/api/v1/auctions/{auctionId}/bids",
				fixture.auctionId()
			)
			.header(HttpHeaders.AUTHORIZATION, accessToken)
			.header("Idempotency-Key", requestId)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"amount\":%d}".formatted(amount)));
	}

	private JsonNode response(MvcResult result) throws IOException {
		return objectMapper.readTree(
			result.getResponse().getContentAsByteArray()
		);
	}

	private void assertOnlyBidResponseFields(JsonNode response) {
		assertThat(response.properties())
			.extracting(Map.Entry::getKey)
			.containsExactlyInAnyOrder(
				"auctionId",
				"myBidAmount",
				"previousBidAmount",
				"additionalHoldAmount",
				"totalHeldAmount",
				"availableBalance",
				"submittedAt"
			);
	}

	private List<String> initializeRedis(Fixture fixture) {
		List<String> keys = RealtimeAuctionRedisKey.bidProcessingKeys(
			fixture.auctionId()
		);
		Map<String, String> state = new HashMap<>();
		state.put("status", "LIVE");
		state.put(
			"endAt",
			Long.toString(Instant.now().plusSeconds(600).toEpochMilli())
		);
		state.put("reservePrice", "50000");
		state.put("sellerId", fixture.sellerId().toString());
		state.put("bidCount", "0");
		state.put("participantCount", "0");
		state.put("version", "0");
		redisTemplate.opsForHash().putAll(keys.get(0), state);
		return keys;
	}

	private Fixture createFixture() {
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		Long sellerId = insertMember("bid-e2e-seller-" + suffix);
		Long bidderId = insertMember("bid-e2e-bidder-" + suffix);
		Long tradeWalletId = insert("""
			INSERT INTO wallet (
				member_id,
				wallet_type,
				available_balance,
				held_balance
			)
			VALUES (?, 'TRADE', ?, 0)
			RETURNING wallet_id
			""", bidderId, INITIAL_BALANCE);
		insert("""
			INSERT INTO wallet (
				member_id,
				wallet_type,
				available_balance,
				held_balance
			)
			VALUES (?, 'BETTING', 2000, 0)
			RETURNING wallet_id
			""", bidderId);
		Long categoryId = insert("""
			INSERT INTO product_category (code, name)
			VALUES (?, ?)
			RETURNING category_id
			""", "BIDE2E_" + suffix, "BID E2E " + suffix);
		Long productId = insert("""
			INSERT INTO product (
				seller_member_id,
				category_id,
				title,
				description
			)
			VALUES (?, ?, 'Bid E2E product', 'BID-06 E2E test')
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
			VALUES (?, 50000, 10, 'IMMEDIATE', 'LIVE')
			RETURNING auction_room_id
			""", productId);
		return new Fixture(
			auctionId,
			sellerId,
			bidderId,
			tradeWalletId
		);
	}

	private String bearerAccessToken(Long memberId) {
		Member member = mock(Member.class);
		when(member.getId()).thenReturn(memberId);
		when(member.getRole()).thenReturn(MemberRole.USER);
		return "Bearer " + jwtTokenProvider.issue(member).accessToken();
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

	private int participantCount(Long auctionId) {
		Integer count = jdbcTemplate.queryForObject("""
			SELECT COUNT(*)
			FROM auction_participant
			WHERE auction_room_id = ?
				AND participation_type = 'BIDDER'
			""", Integer.class, auctionId);
		return count == null ? 0 : count;
	}

	private List<BidRow> bidRows(Long auctionId) {
		return jdbcTemplate.query("""
			SELECT bid.sequence_no, bid.amount, bid.idempotency_key
			FROM bid
			JOIN auction_participant participant
				ON participant.auction_participant_id =
					bid.auction_participant_id
			WHERE participant.auction_room_id = ?
			ORDER BY bid.sequence_no
			""", (resultSet, rowNumber) -> new BidRow(
			resultSet.getInt("sequence_no"),
			resultSet.getLong("amount"),
			resultSet.getString("idempotency_key")
		), auctionId);
	}

	private List<Long> walletBalances(Long walletId) {
		return jdbcTemplate.queryForObject("""
			SELECT available_balance, held_balance
			FROM wallet
			WHERE wallet_id = ?
			""", (resultSet, rowNumber) -> List.of(
			resultSet.getLong("available_balance"),
			resultSet.getLong("held_balance")
		), walletId);
	}

	private List<LedgerDelta> ledgerDeltas(Long walletId) {
		return jdbcTemplate.query("""
			SELECT available_delta, held_delta
			FROM trade_point_ledger
			WHERE wallet_id = ?
			ORDER BY trade_point_ledger_id
			""", (resultSet, rowNumber) -> new LedgerDelta(
			resultSet.getLong("available_delta"),
			resultSet.getLong("held_delta")
		), walletId);
	}

	private String redisValue(String key, String hashKey) {
		Object value = redisTemplate.opsForHash().get(key, hashKey);
		return value == null ? null : value.toString();
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
			waitUntilListening(process, port);
			return new RedisProcess(process, port);
		} catch (Exception exception) {
			throw new ExceptionInInitializerError(exception);
		}
	}

	private static int availablePort() throws IOException {
		try (ServerSocket socket = new ServerSocket(0)) {
			return socket.getLocalPort();
		}
	}

	private static void waitUntilListening(Process process, int port)
		throws Exception {
		for (int attempt = 0; attempt < 100; attempt++) {
			if (!process.isAlive()) {
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

	private record RedisProcess(Process process, int port) {
	}

	private record Fixture(
		Long auctionId,
		Long sellerId,
		Long bidderId,
		Long tradeWalletId
	) {
	}

	private record BidRow(
		int sequenceNo,
		long amount,
		String idempotencyKey
	) {
	}

	private record LedgerDelta(long availableDelta, long heldDelta) {
	}
}
