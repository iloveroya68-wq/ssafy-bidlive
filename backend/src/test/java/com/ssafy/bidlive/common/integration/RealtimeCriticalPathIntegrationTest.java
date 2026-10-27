package com.ssafy.bidlive.common.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.bidlive.common.config.WebSocketProperties;
import com.ssafy.bidlive.common.security.JwtProperties;
import com.ssafy.bidlive.member.domain.MemberRole;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

/** 인증부터 STOMP 채팅·오류와 REST 복원까지 P0 사용자 경로를 실제 계층으로 검증합니다. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(RealtimeCriticalPathIntegrationTest.SubscriptionProbeConfiguration.class)
class RealtimeCriticalPathIntegrationTest
	extends RealtimeIntegrationTestEnvironment {

	private static final String SEND_DESTINATION =
		"/app/auctions/" + RealtimeTestFixture.AUCTION_ID + "/chat";
	private static final String CHAT_DESTINATION =
		"/topic/auctions/" + RealtimeTestFixture.AUCTION_ID + "/chat";
	private static final String QUESTIONS_DESTINATION =
		"/topic/auctions/" + RealtimeTestFixture.AUCTION_ID + "/questions";
	private static final String ERROR_DESTINATION = "/user/queue/errors";
	private static final String SEND_CLIENT_MESSAGE_ID =
		"018f6f4e-2a61-7c00-a1b2-334455667711";

	@LocalServerPort
	private int port;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private StringRedisTemplate redisTemplate;

	@Autowired
	private JwtProperties jwtProperties;

	@Autowired
	private WebSocketProperties webSocketProperties;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private SubscriptionProbe subscriptionProbe;

	private RealtimeTestFixture fixture;
	private RealtimeTestFixture.AuctionFixture auction;

	@BeforeEach
	void setUp() {
		fixture = new RealtimeTestFixture(jdbcTemplate, redisTemplate);
		fixture.reset();
		auction = fixture.createLiveAuction();
		subscriptionProbe.clear();
	}

	@Test
	void sendsPersistsAndRestoresChatWithoutDuplicateEvent() throws Exception {
		String requestBody = objectMapper.writeValueAsString(java.util.Map.of(
			"clientMessageId", SEND_CLIENT_MESSAGE_ID,
			"content", "실시간 통합 채팅",
			"isQuestion", false
		));
		try (RealtimeStompClient client = connect(auction.viewerId())) {
			client.subscribe("chat", CHAT_DESTINATION);
			subscriptionProbe.await(CHAT_DESTINATION);
			client.sendJson(SEND_DESTINATION, requestBody);

			String messageFrame = client.awaitMessageContaining("CHAT_MESSAGE");
			JsonNode event = objectMapper.readTree(
				RealtimeStompClient.bodyOf(messageFrame)
			);
			assertThat(event.path("type").asText()).isEqualTo("CHAT_MESSAGE");
			assertThat(event.path("data").path("clientMessageId").asText())
				.isEqualTo(SEND_CLIENT_MESSAGE_ID);
			assertThat(event.path("data").path("content").asText())
				.isEqualTo("실시간 통합 채팅");

			client.sendJson(SEND_DESTINATION, requestBody);
			assertThat(client.hasMessageContaining(
				SEND_CLIENT_MESSAGE_ID,
				Duration.ofMillis(500)
			)).isFalse();
		}

		assertThat(jdbcTemplate.queryForObject("""
			SELECT COUNT(*)
			FROM chat_message
			WHERE sender_member_id = ? AND client_message_id = ?
			""", Long.class, auction.viewerId(), SEND_CLIENT_MESSAGE_ID))
			.isEqualTo(1L);

		mockMvc.perform(get("/api/v1/auctions/{auctionId}/chats", auction.auctionId())
				.header(HttpHeaders.AUTHORIZATION, bearer(auction.viewerId())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.messages[0].clientMessageId")
				.value(SEND_CLIENT_MESSAGE_ID))
			.andExpect(jsonPath("$.messages[0].content")
				.value("실시간 통합 채팅"));
	}

	@Test
	void publishesSendAndSubscriptionFailuresWithoutClosingConnection()
		throws Exception {
		try (RealtimeStompClient client = connect(auction.viewerId())) {
			client.subscribe("errors", ERROR_DESTINATION);
			subscriptionProbe.await(ERROR_DESTINATION);
			client.subscribe("questions-denied", QUESTIONS_DESTINATION);
			assertThat(client.awaitMessageContaining("SUBSCRIPTION_DENIED"))
				.contains(QUESTIONS_DESTINATION);

			client.sendJson(SEND_DESTINATION, """
				{
				  "clientMessageId": "018f6f4e-2a61-7c00-a1b2-334455667712",
				  "content": "   ",
				  "isQuestion": false
				}
				""");
			assertThat(client.awaitMessageContaining("CHAT_CONTENT_INVALID"))
				.contains("ERROR");

			client.subscribe("chat-after-errors", CHAT_DESTINATION);
			subscriptionProbe.await(CHAT_DESTINATION);
			client.sendJson(SEND_DESTINATION, """
				{
				  "clientMessageId": "018f6f4e-2a61-7c00-a1b2-334455667713",
				  "content": "오류 이후 정상 채팅",
				  "isQuestion": false
				}
				""");
			assertThat(client.awaitMessageContaining("오류 이후 정상 채팅"))
				.contains("CHAT_MESSAGE");
		}
	}

	@Test
	void linksQuestionAndPinnedNoticeRestPathsWithSellerAuthority()
		throws Exception {
		fixture.insertChatMessages(auction);

		mockMvc.perform(get(
				"/api/v1/auctions/{auctionId}/questions",
				auction.auctionId()
			)
				.header(HttpHeaders.AUTHORIZATION, bearer(auction.sellerId())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].questionId")
				.value(RealtimeTestFixture.QUESTION_MESSAGE_ID));

		mockMvc.perform(post(
				"/api/v1/auctions/{auctionId}/chat/pin",
				auction.auctionId()
			)
				.header(HttpHeaders.AUTHORIZATION, bearer(auction.sellerId()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"messageId":"%s"}
					""".formatted(RealtimeTestFixture.CHAT_MESSAGE_ID)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.messageId")
				.value(RealtimeTestFixture.CHAT_MESSAGE_ID));

		mockMvc.perform(post(
				"/api/v1/auctions/{auctionId}/chat/pin",
				auction.auctionId()
			)
				.header(HttpHeaders.AUTHORIZATION, bearer(auction.viewerId()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"messageId":"%s"}
					""".formatted(RealtimeTestFixture.CHAT_MESSAGE_ID)))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("NOT_AUCTION_HOST"));

		assertThat(jdbcTemplate.queryForObject("""
			SELECT is_pinned
			FROM chat_message
			WHERE message_id = ?
			""", Boolean.class, RealtimeTestFixture.CHAT_MESSAGE_ID)).isTrue();
	}

	private RealtimeStompClient connect(long memberId) throws Exception {
		return RealtimeStompClient.connect(
			URI.create(
				"ws://127.0.0.1:" + port + webSocketProperties.getEndpoint()
			),
			webSocketProperties.getAllowedOrigins().getFirst(),
			accessToken(memberId)
		);
	}

	private String bearer(long memberId) {
		return "Bearer " + accessToken(memberId);
	}

	private String accessToken(long memberId) {
		Instant now = Instant.now();
		return Jwts.builder()
			.issuer(jwtProperties.issuer())
			.subject(Long.toString(memberId))
			.issuedAt(Date.from(now))
			.expiration(Date.from(now.plus(Duration.ofMinutes(30))))
			.claim("tokenType", "ACCESS")
			.claim("role", MemberRole.USER.name())
			.signWith(jwtProperties.signingKey(), Jwts.SIG.HS256)
			.compact();
	}

	/** 서버가 실제로 수락한 SUBSCRIBE destination을 수집합니다. */
	static final class SubscriptionProbe
		implements ApplicationListener<SessionSubscribeEvent> {

		private final LinkedBlockingQueue<String> destinations =
			new LinkedBlockingQueue<>();

		@Override
		public void onApplicationEvent(SessionSubscribeEvent event) {
			String destination = StompHeaderAccessor.wrap(event.getMessage())
				.getDestination();
			if (destination != null) {
				destinations.offer(destination);
			}
		}

		void await(String expectedDestination) throws Exception {
			long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
			while (System.nanoTime() < deadline) {
				String destination = destinations.poll(100, TimeUnit.MILLISECONDS);
				if (expectedDestination.equals(destination)) {
					return;
				}
			}
			throw new IllegalStateException(
				"SUBSCRIBE was not accepted: " + expectedDestination
			);
		}

		void clear() {
			destinations.clear();
		}
	}

	/** 통합 테스트가 수락된 STOMP SUBSCRIBE 이벤트를 관찰하도록 Probe Bean을 제공합니다. */
	@TestConfiguration
	static class SubscriptionProbeConfiguration {

		@Bean
		SubscriptionProbe subscriptionProbe() {
			return new SubscriptionProbe();
		}
	}
}
