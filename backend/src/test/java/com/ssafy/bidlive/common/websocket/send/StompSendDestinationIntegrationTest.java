package com.ssafy.bidlive.common.websocket.send;

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
import java.util.List;
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

/** 클라이언트가 broker·사용자 destination으로 직접 보낸 SEND가 폐기되는지 검증합니다. */
@SpringBootTest(
	classes = BidliveBackendApplication.class,
	webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@Import(StompSendDestinationIntegrationTest.TestConfig.class)
class StompSendDestinationIntegrationTest {

	private static final long MEMBER_ID = 401L;
	private static final long OTHER_MEMBER_ID = 402L;
	private static final long AUCTION_ID = 42L;
	private static final String ERROR_DESTINATION = "/user/queue/errors";
	private static final String MODERATION_DESTINATION = "/user/queue/moderation";
	private static final String CHAT_TOPIC_DESTINATION =
		"/topic/auctions/" + AUCTION_ID + "/chat";
	private static final String RENEW_DESTINATION = "/app/auth/renew";
	private static final String FORGED_MARKER = "forged-server-event-marker";
	private static final String FORGED_BODY =
		"{\"type\":\"CHAT\",\"data\":{\"content\":\"" + FORGED_MARKER + "\"}}";

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
				OTHER_MEMBER_ID,
				"LIVE",
				"PUBLIC",
				false,
				Instant.now().plus(Duration.ofMinutes(30))
			)
		));
	}

	@Test
	void dropsSendAddressedDirectlyToBrokerTopic() throws Exception {
		try (TestStompClient client = connect()) {
			client.subscribe("errors", ERROR_DESTINATION);
			subscriptionCollector.await(ERROR_DESTINATION);
			client.subscribe("chat", CHAT_TOPIC_DESTINATION);
			subscriptionCollector.await(CHAT_TOPIC_DESTINATION);

			client.send(CHAT_TOPIC_DESTINATION, "forged-topic", FORGED_BODY);

			assertSendDenied(
				client.awaitMessage(),
				CHAT_TOPIC_DESTINATION,
				"forged-topic"
			);
			assertForgedEventWasNotBroadcast(client);
		}
	}

	@Test
	void dropsSendAddressedDirectlyToUserQueue() throws Exception {
		String otherMemberQueue =
			"/user/" + OTHER_MEMBER_ID + "/queue/moderation";
		String ownQueue = "/user/" + MEMBER_ID + "/queue/moderation";
		try (TestStompClient client = connect()) {
			client.subscribe("errors", ERROR_DESTINATION);
			subscriptionCollector.await(ERROR_DESTINATION);
			client.subscribe("moderation", MODERATION_DESTINATION);
			subscriptionCollector.await(MODERATION_DESTINATION);

			client.send(otherMemberQueue, "forged-other-queue", FORGED_BODY);
			assertSendDenied(
				client.awaitMessage(),
				otherMemberQueue,
				"forged-other-queue"
			);

			client.send(ownQueue, "forged-own-queue", FORGED_BODY);
			assertSendDenied(
				client.awaitMessage(),
				ownQueue,
				"forged-own-queue"
			);
			assertForgedEventWasNotBroadcast(client);
		}
	}

	@Test
	void deliversSendAddressedToApplicationDestination() throws Exception {
		try (TestStompClient client = connect()) {
			client.subscribe("errors", ERROR_DESTINATION);
			subscriptionCollector.await(ERROR_DESTINATION);

			client.send(
				RENEW_DESTINATION,
				"renew-application-destination",
				renewalBody()
			);

			client.awaitReceipt("renew-application-destination");
		}
	}

	/** 위조 SEND가 폐기됐는지 왕복 요청 뒤 남은 frame으로 확인합니다. */
	private void assertForgedEventWasNotBroadcast(TestStompClient client)
		throws Exception {
		client.send(RENEW_DESTINATION, "forgery-roundtrip", renewalBody());
		client.awaitReceipt("forgery-roundtrip");

		assertThat(client.receivedFrames())
			.noneMatch(frame -> frame.contains(FORGED_MARKER));
	}

	private void assertSendDenied(
		String frame,
		String refDestination,
		String correlationId
	) throws Exception {
		JsonNode json = objectMapper.readTree(body(frame));

		assertThat(json.get("type").asText()).isEqualTo("ERROR");
		assertThat(json.get("data").get("code").asText())
			.isEqualTo(StompSendErrorCode.SEND_DENIED.code());
		assertThat(json.get("data").get("message").asText())
			.isEqualTo(StompSendErrorCode.SEND_DENIED.publicMessage());
		assertThat(json.get("data").get("refDestination").asText())
			.isEqualTo(refDestination);
		assertThat(json.get("data").get("correlationId").asText())
			.isEqualTo(correlationId);
		assertThat(json.toString()).doesNotContain(FORGED_MARKER);
	}

	private TestStompClient connect() throws Exception {
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
		client.connect(accessToken());
		assertThat(listener.awaitCommand("CONNECTED", Duration.ofSeconds(5)))
			.startsWith("CONNECTED\n");
		return client;
	}

	private URI webSocketUri() {
		return URI.create(
			"ws://127.0.0.1:" + port + webSocketProperties.getEndpoint()
		);
	}

	private String renewalBody() {
		return "{\"accessToken\":\"" + accessToken() + "\"}";
	}

	private String accessToken() {
		return Jwts.builder()
			.issuer(jwtProperties.issuer())
			.subject(Long.toString(MEMBER_ID))
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

		void clear() {
			destinations.clear();
		}
	}

	/** 통합 테스트에서 수락된 구독 이벤트를 관찰하는 Bean을 제공합니다. */
	@TestConfiguration
	static class TestConfig {

		@Bean
		SubscriptionCollector stompSendDestinationCollector() {
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

		void subscribe(String subscriptionId, String destination)
			throws Exception {
			sendFrame(new StringBuilder()
				.append("SUBSCRIBE\n")
				.append("id:").append(subscriptionId).append('\n')
				.append("destination:").append(destination).append('\n')
				.append("ack:auto\n\n")
				.append('\0')
				.toString());
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

		List<String> receivedFrames() {
			return listener.receivedFrames();
		}

		private void sendFrame(String frame) throws Exception {
			webSocket.sendText(frame, true).get(5, TimeUnit.SECONDS);
		}

		@Override
		public void close() {
			webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "test-complete");
		}
	}

	/** WebSocket callback을 완성된 STOMP frame 단위로 분리하고 수신 이력을 남깁니다. */
	private static class StompFrameListener implements WebSocket.Listener {

		private final StringBuilder pending = new StringBuilder();
		private final LinkedBlockingQueue<String> frames =
			new LinkedBlockingQueue<>();
		private final LinkedBlockingQueue<String> receivedFrames =
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
					String frame = pending.substring(0, frameEnd + 1);
					frames.offer(frame);
					receivedFrames.offer(frame);
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

		List<String> receivedFrames() {
			return List.copyOf(receivedFrames);
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
