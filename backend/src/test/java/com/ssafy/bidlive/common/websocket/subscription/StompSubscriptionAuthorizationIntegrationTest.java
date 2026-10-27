package com.ssafy.bidlive.common.websocket.subscription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
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
import com.ssafy.bidlive.auction.application.AuctionOwnershipReader;
import com.ssafy.bidlive.auction.application.CatchUpRoomAccess;
import com.ssafy.bidlive.auction.application.CatchUpRoomAccessReader;
import com.ssafy.bidlive.common.config.WebSocketProperties;
import com.ssafy.bidlive.common.security.JwtProperties;
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

/** 실제 STOMP 연결에서 판매자 구독 권한·방 공개 여부·개인 오류·연결 유지를 검증합니다. */
@SpringBootTest(
	classes = BidliveBackendApplication.class,
	webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@Import(StompSubscriptionAuthorizationIntegrationTest.TestConfig.class)
class StompSubscriptionAuthorizationIntegrationTest {

	private static final long SELLER_ID = 201L;
	private static final long OTHER_MEMBER_ID = 202L;
	private static final long FORGED_MEMBER_ID = 999_999L;
	private static final long AUCTION_ID = 42L;
	private static final String USER_ERROR_DESTINATION = "/user/queue/errors";
	private static final String QUESTIONS_DESTINATION =
		"/topic/auctions/42/questions";
	private static final String PUBLIC_DESTINATION =
		"/topic/auctions/42/chat";
	private static final String UNKNOWN_DESTINATION = "/topic/private";

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
	private AuctionOwnershipReader auctionOwnershipReader;

	@MockitoBean
	private CatchUpRoomAccessReader roomAccessReader;

	@BeforeEach
	void setUp() {
		subscriptionCollector.clear();
		Member seller = activeMember();
		Member otherMember = activeMember();
		when(memberPersistence.findById(SELLER_ID))
			.thenReturn(Optional.of(seller));
		when(memberPersistence.findById(OTHER_MEMBER_ID))
			.thenReturn(Optional.of(otherMember));
		when(auctionOwnershipReader.isSeller(AUCTION_ID, SELLER_ID))
			.thenReturn(true);
		when(auctionOwnershipReader.isSeller(AUCTION_ID, OTHER_MEMBER_ID))
			.thenReturn(false);
		stubRoomVisibility("PUBLIC");
	}

	@Test
	void deniesNonSellerPublicTopicOnPrivateRoomAndKeepsConnectionOpen() throws Exception {
		stubRoomVisibility("PRIVATE");
		try (TestStompClient client = connect(OTHER_MEMBER_ID)) {
			client.subscribe("errors", USER_ERROR_DESTINATION, null);
			subscriptionCollector.await(USER_ERROR_DESTINATION);

			client.subscribe(
				"private-denied",
				PUBLIC_DESTINATION,
				"private-receipt"
			);
			assertDeniedError(
				client.awaitMessage(),
				PUBLIC_DESTINATION,
				"private-receipt"
			);
			assertThat(subscriptionCollector.wasObserved(PUBLIC_DESTINATION))
				.isFalse();

			client.subscribe("errors-after-denial", USER_ERROR_DESTINATION, null);
			subscriptionCollector.await(USER_ERROR_DESTINATION);
		}
	}

	@Test
	void allowsSellerPublicTopicOnPrivateRoom() throws Exception {
		stubRoomVisibility("PRIVATE");
		try (TestStompClient client = connect(SELLER_ID)) {
			client.subscribe("private-seller", PUBLIC_DESTINATION, null);
			subscriptionCollector.await(PUBLIC_DESTINATION);
		}
	}

	private void stubRoomVisibility(String visibility) {
		when(roomAccessReader.findAccess(AUCTION_ID)).thenReturn(Optional.of(
			new CatchUpRoomAccess(
				AUCTION_ID,
				SELLER_ID,
				"LIVE",
				visibility,
				false,
				Instant.now().plus(Duration.ofMinutes(10))
			)
		));
	}

	@Test
	void allowsSellerQuestionsAndAuthenticatedDestinations() throws Exception {
		try (TestStompClient client = connect(SELLER_ID)) {
			client.subscribe("errors", USER_ERROR_DESTINATION, null);
			subscriptionCollector.await(USER_ERROR_DESTINATION);

			client.subscribe("questions", QUESTIONS_DESTINATION, null);
			subscriptionCollector.await(QUESTIONS_DESTINATION);

			client.subscribe("public", PUBLIC_DESTINATION, null);
			subscriptionCollector.await(PUBLIC_DESTINATION);
		}

		verify(auctionOwnershipReader).isSeller(AUCTION_ID, SELLER_ID);
	}

	@Test
	void deniesNonSellerQuestionsAndKeepsConnectionOpen() throws Exception {
		try (TestStompClient client = connect(OTHER_MEMBER_ID)) {
			client.subscribe("errors", USER_ERROR_DESTINATION, null);
			subscriptionCollector.await(USER_ERROR_DESTINATION);

			client.subscribe(
				"questions-denied",
				QUESTIONS_DESTINATION,
				"questions-receipt"
			);
			assertDeniedError(
				client.awaitMessage(),
				QUESTIONS_DESTINATION,
				"questions-receipt"
			);
			assertThat(subscriptionCollector.wasObserved(QUESTIONS_DESTINATION))
				.isFalse();

			client.subscribe("public-after-denial", PUBLIC_DESTINATION, null);
			subscriptionCollector.await(PUBLIC_DESTINATION);
		}

		verify(auctionOwnershipReader).isSeller(
			AUCTION_ID,
			OTHER_MEMBER_ID
		);
	}

	@Test
	void deniesUnregisteredDestinationByDefault() throws Exception {
		try (TestStompClient client = connect(SELLER_ID)) {
			client.subscribe("errors", USER_ERROR_DESTINATION, null);
			subscriptionCollector.await(USER_ERROR_DESTINATION);

			client.subscribe(
				"unknown-denied",
				UNKNOWN_DESTINATION,
				"unknown-receipt"
			);
			assertDeniedError(
				client.awaitMessage(),
				UNKNOWN_DESTINATION,
				"unknown-receipt"
			);
			assertThat(subscriptionCollector.wasObserved(UNKNOWN_DESTINATION))
				.isFalse();
		}
	}

	@Test
	void deniesQuestionsWhenNonSellerSuppliesSellerIdentityHeaders() throws Exception {
		try (TestStompClient client = connect(OTHER_MEMBER_ID)) {
			client.subscribe("errors", USER_ERROR_DESTINATION, null);
			subscriptionCollector.await(USER_ERROR_DESTINATION);

			client.subscribe(
				"spoofed-questions",
				QUESTIONS_DESTINATION,
				"spoofed-questions-receipt",
				"memberId:" + SELLER_ID,
				"user:" + SELLER_ID,
				"simpUser:" + SELLER_ID
			);
			assertDeniedError(
				client.awaitMessage(),
				QUESTIONS_DESTINATION,
				"spoofed-questions-receipt"
			);
			assertThat(subscriptionCollector.wasObserved(QUESTIONS_DESTINATION))
				.isFalse();

			client.subscribe("public-after-spoof", PUBLIC_DESTINATION, null);
			subscriptionCollector.await(PUBLIC_DESTINATION);
		}

		verify(auctionOwnershipReader).isSeller(
			AUCTION_ID,
			OTHER_MEMBER_ID
		);
	}

	@Test
	void deniesDestinationThatAttemptsToAddressAnotherUserQueue() throws Exception {
		String foreignUserQueue = "/user/" + FORGED_MEMBER_ID + "/queue/errors";
		try (TestStompClient client = connect(SELLER_ID)) {
			client.subscribe("errors", USER_ERROR_DESTINATION, null);
			subscriptionCollector.await(USER_ERROR_DESTINATION);

			client.subscribe(
				"foreign-user-queue",
				foreignUserQueue,
				"foreign-user-queue-receipt"
			);
			assertDeniedError(
				client.awaitMessage(),
				foreignUserQueue,
				"foreign-user-queue-receipt"
			);
			assertThat(subscriptionCollector.wasObserved(foreignUserQueue))
				.isFalse();
		}
	}

	private TestStompClient connect(long memberId) throws Exception {
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
		client.connect(accessToken(memberId));
		assertThat(listener.awaitCommand("CONNECTED", Duration.ofSeconds(5)))
			.startsWith("CONNECTED\n");
		return client;
	}

	private URI webSocketUri() {
		return URI.create(
			"ws://127.0.0.1:"
				+ port
				+ webSocketProperties.getEndpoint()
		);
	}

	private String accessToken(long memberId) {
		return Jwts.builder()
			.issuer(jwtProperties.issuer())
			.subject(Long.toString(memberId))
			.expiration(Date.from(Instant.now().plus(Duration.ofMinutes(30))))
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

	private void assertDeniedError(
		String frame,
		String refDestination,
		String correlationId
	) throws Exception {
		JsonNode json = objectMapper.readTree(body(frame));

		assertThat(json.get("type").asText()).isEqualTo("ERROR");
		assertThat(json.get("data").get("code").asText())
			.isEqualTo("SUBSCRIPTION_DENIED");
		assertThat(json.get("data").get("refDestination").asText())
			.isEqualTo(refDestination);
		assertThat(json.get("data").get("correlationId").asText())
			.isEqualTo(correlationId);
		assertThat(json.toString())
			.doesNotContain(Long.toString(SELLER_ID))
			.doesNotContain(Long.toString(OTHER_MEMBER_ID))
			.doesNotContain("SecurityPrincipal")
			.doesNotContain("AuctionOwnershipReader");
	}

	private String body(String frame) {
		int bodyStart = frame.indexOf("\n\n");
		int frameEnd = frame.lastIndexOf('\0');
		if (bodyStart < 0 || frameEnd < bodyStart) {
			throw new IllegalArgumentException(
				"STOMP MESSAGE body를 찾을 수 없습니다."
			);
		}
		return frame.substring(bodyStart + 2, frameEnd);
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
						"구독 완료 이벤트를 받지 못했습니다: "
							+ expectedDestination
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
		SubscriptionCollector stompSubscriptionAuthorizationCollector() {
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
			send(new StringBuilder()
				.append("CONNECT\n")
				.append("accept-version:1.2\n")
				.append("heart-beat:0,0\n")
				.append("Authorization:Bearer ")
				.append(token)
				.append("\n\n")
				.append('\0')
				.toString());
		}

		void subscribe(
			String subscriptionId,
			String destination,
			String receiptId,
			String... additionalHeaders
		) throws Exception {
			StringBuilder frame = new StringBuilder()
				.append("SUBSCRIBE\n")
				.append("id:").append(subscriptionId).append('\n')
				.append("destination:").append(destination).append('\n')
				.append("ack:auto\n");
			if (receiptId != null) {
				frame.append("receipt:").append(receiptId).append('\n');
			}
			for (String additionalHeader : additionalHeaders) {
				frame.append(additionalHeader).append('\n');
			}
			send(frame.append('\n').append('\0').toString());
		}

		String awaitMessage() throws Exception {
			return listener.awaitCommand("MESSAGE", Duration.ofSeconds(5));
		}

		private void send(String frame) throws Exception {
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
				long remaining = deadline - System.nanoTime();
				if (remaining <= 0) {
					throw new IllegalStateException(
						command + " frame을 제한 시간 안에 받지 못했습니다."
					);
				}
				String frame = frames.poll(remaining, TimeUnit.NANOSECONDS);
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
	}
}
