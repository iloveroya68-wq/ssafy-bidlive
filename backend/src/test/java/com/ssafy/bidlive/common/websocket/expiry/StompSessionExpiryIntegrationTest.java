package com.ssafy.bidlive.common.websocket.expiry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.bidlive.BidliveBackendApplication;
import com.ssafy.bidlive.auction.application.CatchUpRoomAccess;
import com.ssafy.bidlive.auction.application.CatchUpRoomAccessReader;
import com.ssafy.bidlive.common.config.WebSocketProperties;
import com.ssafy.bidlive.common.security.JwtProperties;
import com.ssafy.bidlive.common.security.StompAuthenticationErrorCode;
import com.ssafy.bidlive.common.websocket.send.StompSendErrorCode;
import com.ssafy.bidlive.member.application.MemberPersistence;
import com.ssafy.bidlive.member.domain.Member;
import com.ssafy.bidlive.member.domain.MemberRole;
import com.ssafy.bidlive.member.domain.MemberStatus;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

/** 만료된 STOMP 세션의 SEND·SUBSCRIBE 차단, 유예 통과와 재인증 회복 경로를 검증합니다. */
@SpringBootTest(
	classes = BidliveBackendApplication.class,
	webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
	properties = "app.websocket.token-expiry-grace=2s"
)
@Import(StompSessionExpiryIntegrationTest.TestConfig.class)
class StompSessionExpiryIntegrationTest {

	private static final long MEMBER_ID = 501L;
	private static final long SELLER_ID = 502L;
	private static final long AUCTION_ID = 42L;
	private static final Duration GRACE = Duration.ofSeconds(2);
	private static final Duration SHORT_TOKEN_TTL = Duration.ofSeconds(2);
	private static final Duration LONG_TOKEN_TTL = Duration.ofMinutes(30);
	private static final String ERROR_DESTINATION = "/user/queue/errors";
	private static final String CHAT_TOPIC_DESTINATION =
		"/topic/auctions/" + AUCTION_ID + "/chat";
	private static final String RENEW_DESTINATION = "/app/auth/renew";

	@LocalServerPort
	private int port;

	@Autowired
	private JwtProperties jwtProperties;

	@Autowired
	private WebSocketProperties webSocketProperties;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private SubscriptionCollector subscriptionCollector;

	@MockitoBean
	private MemberPersistence memberPersistence;

	@MockitoBean
	private CatchUpRoomAccessReader roomAccessReader;

	@BeforeEach
	void setUp() {
		subscriptionCollector.clear();
		Member member = activeMember();
		when(memberPersistence.findById(MEMBER_ID))
			.thenReturn(Optional.of(member));
		when(roomAccessReader.findAccess(AUCTION_ID)).thenReturn(Optional.of(
			new CatchUpRoomAccess(
				AUCTION_ID,
				SELLER_ID,
				"LIVE",
				"PUBLIC",
				false,
				Instant.now().plus(Duration.ofMinutes(30))
			)
		));
	}

	@Test
	void deniesSubscribeFromExpiredSessionAndPublishesAuthExpired()
		throws Exception {
		try (Session session = connect(SHORT_TOKEN_TTL)) {
			session.client().subscribe("errors", ERROR_DESTINATION, null);
			subscriptionCollector.await(ERROR_DESTINATION);

			sleepUntilExpired(session.expiresAt());
			session.client().subscribe(
				"chat-after-expiry",
				CHAT_TOPIC_DESTINATION,
				"expired-subscribe"
			);

			assertAuthExpired(
				session.client().awaitMessage(),
				CHAT_TOPIC_DESTINATION,
				"expired-subscribe"
			);
			assertThat(subscriptionCollector.wasObserved(CHAT_TOPIC_DESTINATION))
				.isFalse();
		}
	}

	/** 만료 검사가 destination 검사보다 앞서야 클라이언트가 재인증할 이유를 알 수 있습니다. */
	@Test
	void deniesSendFromExpiredSessionWithAuthExpired() throws Exception {
		try (Session session = connect(SHORT_TOKEN_TTL)) {
			session.client().subscribe("errors", ERROR_DESTINATION, null);
			subscriptionCollector.await(ERROR_DESTINATION);

			sleepUntilExpired(session.expiresAt());
			session.client().send(
				CHAT_TOPIC_DESTINATION,
				"expired-send",
				chatBody()
			);

			assertAuthExpired(
				session.client().awaitMessage(),
				CHAT_TOPIC_DESTINATION,
				"expired-send"
			);
		}
	}

	@Test
	void allowsFrameInsideGraceWindow() throws Exception {
		try (Session session = connect(SHORT_TOKEN_TTL)) {
			sleepUntil(session.expiresAt().plusMillis(300));

			session.client().subscribe(
				"chat-in-grace",
				CHAT_TOPIC_DESTINATION,
				null
			);

			subscriptionCollector.await(CHAT_TOPIC_DESTINATION);
		}
	}

	@Test
	void allowsRenewFromExpiredSessionAndRestoresNormalFrames()
		throws Exception {
		try (Session session = connect(SHORT_TOKEN_TTL)) {
			session.client().subscribe("errors", ERROR_DESTINATION, null);
			subscriptionCollector.await(ERROR_DESTINATION);

			sleepUntilExpired(session.expiresAt());
			session.client().send(
				RENEW_DESTINATION,
				"renew-after-expiry",
				renewalBody(Instant.now().plus(LONG_TOKEN_TTL))
			);
			session.client().awaitReceipt("renew-after-expiry");

			session.client().subscribe(
				"chat-after-renew",
				CHAT_TOPIC_DESTINATION,
				null
			);
			subscriptionCollector.await(CHAT_TOPIC_DESTINATION);
		}
	}

	@Test
	void keepsSessionWithValidTokenUnaffected() throws Exception {
		try (Session session = connect(LONG_TOKEN_TTL)) {
			session.client().subscribe("errors", ERROR_DESTINATION, null);
			subscriptionCollector.await(ERROR_DESTINATION);

			session.client().subscribe("chat", CHAT_TOPIC_DESTINATION, null);
			subscriptionCollector.await(CHAT_TOPIC_DESTINATION);

			session.client().send(
				CHAT_TOPIC_DESTINATION,
				"valid-session-send",
				chatBody()
			);

			assertErrorCode(
				session.client().awaitMessage(),
				StompSendErrorCode.SEND_DENIED.code(),
				StompSendErrorCode.SEND_DENIED.publicMessage(),
				CHAT_TOPIC_DESTINATION,
				"valid-session-send"
			);
		}
	}

	private void assertAuthExpired(
		String frame,
		String refDestination,
		String correlationId
	) throws Exception {
		assertErrorCode(
			frame,
			StompAuthenticationErrorCode.AUTH_EXPIRED.code(),
			StompAuthenticationErrorCode.AUTH_EXPIRED.publicMessage(),
			refDestination,
			correlationId
		);
	}

	private void assertErrorCode(
		String frame,
		String expectedCode,
		String expectedMessage,
		String refDestination,
		String correlationId
	) throws Exception {
		JsonNode json = objectMapper.readTree(body(frame));

		assertThat(json.get("type").asText()).isEqualTo("ERROR");
		assertThat(json.get("data").get("code").asText())
			.isEqualTo(expectedCode);
		assertThat(json.get("data").get("message").asText())
			.isEqualTo(expectedMessage);
		assertThat(json.get("data").get("refDestination").asText())
			.isEqualTo(refDestination);
		assertThat(json.get("data").get("correlationId").asText())
			.isEqualTo(correlationId);
	}

	private void sleepUntilExpired(Instant expiresAt) throws Exception {
		sleepUntil(expiresAt.plus(GRACE).plusMillis(500));
	}

	private void sleepUntil(Instant target) throws Exception {
		long remainingMillis = Duration.between(Instant.now(), target).toMillis();
		if (remainingMillis > 0) {
			Thread.sleep(remainingMillis);
		}
	}

	private Session connect(Duration tokenTtl) throws Exception {
		StompFrameListener listener = new StompFrameListener();
		WebSocket webSocket = HttpClient.newHttpClient()
			.newWebSocketBuilder()
			.header(
				"Origin",
				webSocketProperties.getAllowedOrigins().getFirst()
			)
			.subprotocols("v12.stomp")
			.buildAsync(webSocketUri(), listener)
			.get(5, TimeUnit.SECONDS);

		TestStompClient client = new TestStompClient(webSocket, listener);
		Instant expiresAt = Instant.now().plus(tokenTtl);
		client.connect(accessToken(expiresAt));
		assertThat(listener.awaitCommand("CONNECTED", Duration.ofSeconds(5)))
			.startsWith("CONNECTED\n");
		return new Session(client, expiresAt);
	}

	private URI webSocketUri() {
		return URI.create(
			"ws://127.0.0.1:" + port + webSocketProperties.getEndpoint()
		);
	}

	private String renewalBody(Instant expiresAt) {
		return "{\"accessToken\":\"" + accessToken(expiresAt) + "\"}";
	}

	private String chatBody() {
		return "{\"type\":\"CHAT\",\"data\":{\"content\":\"expired-session\"}}";
	}

	private String accessToken(Instant expiresAt) {
		return Jwts.builder()
			.issuer(jwtProperties.issuer())
			.subject(Long.toString(MEMBER_ID))
			.expiration(Date.from(expiresAt))
			.claim("tokenType", "ACCESS")
			.claim("role", MemberRole.USER.name())
			.signWith(jwtProperties.signingKey(), Jwts.SIG.HS256)
			.compact();
	}

	private Member activeMember() {
		Member member = mock(Member.class);
		when(member.getStatus()).thenReturn(MemberStatus.ACTIVE);
		when(member.getRole()).thenReturn(MemberRole.USER);
		return member;
	}

	private String body(String frame) {
		String normalized = frame.stripLeading();
		int bodyStart = normalized.indexOf("\n\n");
		int frameEnd = normalized.lastIndexOf('\0');
		if (bodyStart < 0 || frameEnd < bodyStart) {
			throw new IllegalArgumentException(
				"STOMP MESSAGE body를 찾을 수 없습니다."
			);
		}
		return normalized.substring(bodyStart + 2, frameEnd);
	}

	/** 만료 시각을 알아야 유예 경계를 계산할 수 있어 토큰 만료 시각을 연결과 함께 보관합니다. */
	private record Session(TestStompClient client, Instant expiresAt)
		implements AutoCloseable {

		@Override
		public void close() {
			client.close();
		}
	}

	/** 실제 broker가 수락한 SUBSCRIBE Destination을 수집합니다. */
	static class SubscriptionCollector
		implements ApplicationListener<SessionSubscribeEvent> {

		private final LinkedBlockingQueue<String> destinations =
			new LinkedBlockingQueue<>();

		@Override
		public void onApplicationEvent(SessionSubscribeEvent event) {
			StompHeaderAccessor accessor = StompHeaderAccessor.wrap(
				event.getMessage()
			);
			destinations.offer(accessor.getDestination());
		}

		void await(String expectedDestination) throws Exception {
			long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
			while (true) {
				long remaining = deadline - System.nanoTime();
				if (remaining <= 0) {
					throw new IllegalStateException(
						"구독 완료 이벤트를 받지 못했습니다: " + expectedDestination
					);
				}
				String destination = destinations.poll(
					remaining,
					TimeUnit.NANOSECONDS
				);
				if (expectedDestination.equals(destination)) {
					return;
				}
			}
		}

		boolean wasObserved(String destination) {
			return destinations.contains(destination);
		}

		void clear() {
			destinations.clear();
		}
	}

	/** 통합 테스트에서 수락된 구독 이벤트를 관찰하는 Bean을 제공합니다. */
	@TestConfiguration
	static class TestConfig {

		@Bean
		SubscriptionCollector stompSessionExpiryCollector() {
			return new SubscriptionCollector();
		}
	}

	/** 실제 WebSocket 연결에 STOMP frame을 전송합니다. */
	private static class TestStompClient implements AutoCloseable {

		private final WebSocket webSocket;
		private final StompFrameListener listener;

		TestStompClient(WebSocket webSocket, StompFrameListener listener) {
			this.webSocket = webSocket;
			this.listener = listener;
		}

		void connect(String token) throws Exception {
			sendFrame(new StringBuilder()
				.append("CONNECT\n")
				.append("accept-version:1.2\n")
				.append("heart-beat:0,0\n")
				.append("Authorization:Bearer ").append(token)
				.append("\n\n")
				.append('\0')
				.toString());
		}

		void subscribe(
			String subscriptionId,
			String destination,
			String receiptId
		) throws Exception {
			StringBuilder frame = new StringBuilder()
				.append("SUBSCRIBE\n")
				.append("id:").append(subscriptionId).append('\n')
				.append("destination:").append(destination).append('\n')
				.append("ack:auto\n");
			if (receiptId != null) {
				frame.append("receipt:").append(receiptId).append('\n');
			}
			sendFrame(frame.append('\n').append('\0').toString());
		}

		void send(String destination, String receiptId, String body)
			throws Exception {
			sendFrame(new StringBuilder()
				.append("SEND\n")
				.append("destination:").append(destination).append('\n')
				.append("content-type:application/json\n")
				.append("receipt:").append(receiptId).append('\n')
				.append("content-length:")
				.append(body.getBytes(StandardCharsets.UTF_8).length)
				.append("\n\n")
				.append(body)
				.append('\0')
				.toString());
		}

		String awaitMessage() throws Exception {
			return listener.awaitCommand("MESSAGE", Duration.ofSeconds(5));
		}

		void awaitReceipt(String receiptId) throws Exception {
			listener.awaitReceipt(receiptId, Duration.ofSeconds(5));
		}

		private void sendFrame(String frame) throws Exception {
			webSocket.sendText(frame, true).get(5, TimeUnit.SECONDS);
		}

		@Override
		public void close() {
			webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "test-complete");
		}
	}

	/** WebSocket callback을 완성된 STOMP frame 단위로 분리합니다. */
	private static class StompFrameListener implements WebSocket.Listener {

		private final StringBuilder pending = new StringBuilder();
		private final LinkedBlockingQueue<String> frames =
			new LinkedBlockingQueue<>();

		@Override
		public void onOpen(WebSocket webSocket) {
			webSocket.request(1);
		}

		@Override
		public CompletionStage<?> onText(
			WebSocket webSocket,
			CharSequence data,
			boolean last
		) {
			synchronized (pending) {
				pending.append(data);
				int frameEnd;
				while ((frameEnd = pending.indexOf("\0")) >= 0) {
					frames.offer(pending.substring(0, frameEnd + 1));
					pending.delete(0, frameEnd + 1);
				}
			}
			webSocket.request(1);
			return null;
		}

		@Override
		public CompletionStage<?> onBinary(
			WebSocket webSocket,
			ByteBuffer data,
			boolean last
		) {
			webSocket.request(1);
			return null;
		}

		String awaitCommand(String command, Duration timeout) throws Exception {
			long deadline = System.nanoTime() + timeout.toNanos();
			while (true) {
				String frame = pollFrame(deadline);
				if (frame == null) {
					throw new IllegalStateException(
						command + " frame을 제한 시간 안에 받지 못했습니다."
					);
				}
				String normalized = frame.stripLeading();
				if (normalized.startsWith(command + "\n")) {
					return normalized;
				}
			}
		}

		void awaitReceipt(String receiptId, Duration timeout) throws Exception {
			long deadline = System.nanoTime() + timeout.toNanos();
			while (true) {
				String frame = pollFrame(deadline);
				if (frame == null) {
					throw new IllegalStateException(
						"RECEIPT " + receiptId + " frame을 제한 시간 안에 받지 못했습니다."
					);
				}
				String normalized = frame.stripLeading();
				if (normalized.startsWith("RECEIPT\n")
					&& normalized.contains("receipt-id:" + receiptId + "\n")) {
					return;
				}
			}
		}

		private String pollFrame(long deadline) throws Exception {
			long remaining = deadline - System.nanoTime();
			if (remaining <= 0) {
				return null;
			}
			return frames.poll(remaining, TimeUnit.NANOSECONDS);
		}
	}
}
