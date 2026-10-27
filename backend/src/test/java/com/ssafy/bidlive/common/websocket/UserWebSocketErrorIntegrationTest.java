package com.ssafy.bidlive.common.websocket;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.bidlive.auction.application.AuctionOwnershipReader;
import com.ssafy.bidlive.auction.application.CatchUpRoomAccessReader;
import com.ssafy.bidlive.common.observability.RealtimeObservationMetrics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientWebSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/** 실제 STOMP 연결에서 개인 오류의 사용자·세션 격리와 연결 유지를 검증합니다. */
@SpringBootTest(
	classes = UserWebSocketErrorIntegrationTest.TestApplication.class,
	webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class UserWebSocketErrorIntegrationTest {

	private static final String PRIMARY_MEMBER_ID = "201";
	private static final String OTHER_MEMBER_ID = "202";
	private static final String ENDPOINT = "/ws";
	private static final String ALLOWED_ORIGIN = "http://localhost:5173";
	private static final String USER_ERROR_DESTINATION = "/user/queue/errors";
	private static final String TEST_SEND_DESTINATION = "/app/test/user-error";
	private static final String TEST_PRINCIPAL_HEADER = "Test-Principal";
	private static final String TOKEN_MARKER_PREFIX = "test-token-must-not-be-exposed-";

	@LocalServerPort
	private int port;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private TestStompClient.SubscriptionCollector subscriptionCollector;

	@MockitoBean
	private AuctionOwnershipReader auctionOwnershipReader;

	@MockitoBean
	private CatchUpRoomAccessReader roomAccessReader;

	@MockitoBean
	private RealtimeObservationMetrics metrics;

	@Test
	void sendsErrorOnlyToOriginatingSessionAndKeepsConnectionOpen() throws Exception {
		try (
			TestStompClient originSession = connect(PRIMARY_MEMBER_ID);
			TestStompClient sameMemberSession = connect(PRIMARY_MEMBER_ID);
			TestStompClient otherMemberSession = connect(OTHER_MEMBER_ID)
		) {
			originSession.subscribe("errors-origin");
			sameMemberSession.subscribe("errors-same-member");
			otherMemberSession.subscribe("errors-other-member");

			subscriptionCollector.awaitSubscriptions(3);

			originSession.sendErrorRequest("request-origin-1");

			String firstMessage = originSession.awaitMessage();
			assertErrorMessage(firstMessage, "request-origin-1", originSession.tokenMarker());
			assertThat(sameMemberSession.pollMessage(Duration.ofMillis(700))).isNull();
			assertThat(otherMemberSession.pollMessage(Duration.ofMillis(700))).isNull();

			originSession.sendErrorRequest("request-origin-2");

			String secondMessage = originSession.awaitMessage();
			assertErrorMessage(secondMessage, "request-origin-2", originSession.tokenMarker());
		}
	}

	private TestStompClient connect(String principalName) throws Exception {
		String tokenMarker = TOKEN_MARKER_PREFIX + principalName;
		StompFrameListener listener = new StompFrameListener();
		WebSocket webSocket = HttpClient.newHttpClient()
			.newWebSocketBuilder()
			.header("Origin", ALLOWED_ORIGIN)
			.subprotocols("v12.stomp")
			.buildAsync(webSocketUri(), listener)
			.get(5, TimeUnit.SECONDS);
		TestStompClient client = new TestStompClient(webSocket, listener, tokenMarker);
		client.send(connectFrame(principalName, tokenMarker));
		assertThat(listener.awaitCommand("CONNECTED", Duration.ofSeconds(5)))
			.startsWith("CONNECTED\n");
		return client;
	}

	private URI webSocketUri() {
		return URI.create("ws://127.0.0.1:" + port + ENDPOINT);
	}

	private String connectFrame(String principalName, String tokenMarker) {
		return new StringBuilder()
			.append("CONNECT\n")
			.append("accept-version:1.2\n")
			.append("heart-beat:0,0\n")
			.append(TEST_PRINCIPAL_HEADER).append(':').append(principalName).append('\n')
			.append("Authorization:Bearer ").append(tokenMarker).append("\n\n")
			.append('\0')
			.toString();
	}

	private void assertErrorMessage(
		String frame,
		String correlationId,
		String hiddenToken
	) throws Exception {
		String body = body(frame);
		JsonNode json = objectMapper.readTree(body);

		assertThat(json.get("type").asText()).isEqualTo("ERROR");
		assertThat(json.get("data").get("code").asText())
			.isEqualTo("SUBSCRIPTION_DENIED");
		assertThat(json.get("data").get("message").asText())
			.isEqualTo("해당 채널을 구독할 권한이 없습니다.");
		assertThat(json.get("data").get("refDestination").asText())
			.isEqualTo(TEST_SEND_DESTINATION);
		assertThat(json.get("data").get("correlationId").asText())
			.isEqualTo(correlationId);
		assertThat(body)
			.doesNotContain(hiddenToken)
			.doesNotContain(PRIMARY_MEMBER_ID)
			.doesNotContain("StompAuthenticationException")
			.doesNotContain("RuntimeException")
			.doesNotContain("ACTIVE");
	}

	private String body(String frame) {
		String normalized = frame.stripLeading();
		int bodyStart = normalized.indexOf("\n\n");
		int frameEnd = normalized.lastIndexOf('\0');
		if (bodyStart < 0 || frameEnd < bodyStart) {
			throw new IllegalArgumentException("STOMP MESSAGE body를 찾을 수 없습니다.");
		}
		return normalized.substring(bodyStart + 2, frameEnd);
	}

	/** DB 없이 WS-04 메시지 전달 구성만 실행하는 테스트 애플리케이션입니다. */
	@SpringBootApplication(exclude = {
		DataSourceAutoConfiguration.class,
		HibernateJpaAutoConfiguration.class,
		FlywayAutoConfiguration.class,
		SecurityAutoConfiguration.class,
		ManagementWebSecurityAutoConfiguration.class,
		UserDetailsServiceAutoConfiguration.class,
		OAuth2ClientWebSecurityAutoConfiguration.class
	})
	@EnableWebSocketMessageBroker
	@Import(UserErrorTestHandler.class)
	static class TestApplication implements WebSocketMessageBrokerConfigurer {

		@Bean
        TestStompClient.SubscriptionCollector subscriptionCollector() {
			return new TestStompClient.SubscriptionCollector();
		}

		@Override
		public void registerStompEndpoints(StompEndpointRegistry registry) {
			registry.addEndpoint(ENDPOINT).setAllowedOrigins(ALLOWED_ORIGIN);
		}

		@Override
		public void configureMessageBroker(MessageBrokerRegistry registry) {
			registry.setApplicationDestinationPrefixes("/app");
			registry.setUserDestinationPrefix("/user");
			registry.enableSimpleBroker("/topic", "/queue");
		}

		@Override
		public void configureClientInboundChannel(ChannelRegistration registration) {
			registration.interceptors(new TestPrincipalInterceptor());
		}

	}

	/** CONNECT의 테스트 전용 header를 서버 Principal로 설정합니다. */
	private static class TestPrincipalInterceptor implements ChannelInterceptor {

		@Override
		public Message<?> preSend(Message<?> message, MessageChannel channel) {
			StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(
				message,
				StompHeaderAccessor.class
			);
			if (accessor == null || accessor.getCommand() != StompCommand.CONNECT) {
				return message;
			}

			List<String> principalHeaders = accessor.getNativeHeader(
				TEST_PRINCIPAL_HEADER
			);
			if (principalHeaders == null || principalHeaders.size() != 1) {
				throw new IllegalArgumentException("테스트 Principal header가 필요합니다.");
			}
			accessor.setUser(new TestPrincipal(principalHeaders.getFirst()));
			return message;
		}
	}

	/** 테스트 전용 SEND 요청을 개인 오류 발행 계약으로 연결합니다. */
	@Controller
	static class UserErrorTestHandler {

		private final StompErrorContextResolver contextResolver;
		private final UserWebSocketErrorPublisher errorPublisher;

		UserErrorTestHandler(
			StompErrorContextResolver contextResolver,
			UserWebSocketErrorPublisher errorPublisher
		) {
			this.contextResolver = contextResolver;
			this.errorPublisher = errorPublisher;
		}

		@MessageMapping("/test/user-error")
		void publish(Message<byte[]> message) {
			errorPublisher.publish(
				contextResolver.resolve(message),
				TestWebSocketErrorCode.SUBSCRIPTION_DENIED
			);
		}
	}

	private enum TestWebSocketErrorCode implements WebSocketErrorCode {

		SUBSCRIPTION_DENIED("해당 채널을 구독할 권한이 없습니다.");

		private final String publicMessage;

		TestWebSocketErrorCode(String publicMessage) {
			this.publicMessage = publicMessage;
		}

		@Override
		public String code() {
			return name();
		}

		@Override
		public String publicMessage() {
			return publicMessage;
		}
	}

	private record TestPrincipal(String name) implements java.security.Principal {

		@Override
		public String getName() {
			return name;
		}
	}

	/** 실제 WebSocket 연결에 STOMP frame을 보내고 응답을 기다립니다. */
	private static class TestStompClient implements AutoCloseable {

		private final WebSocket webSocket;
		private final StompFrameListener listener;
		private final String tokenMarker;

		TestStompClient(
			WebSocket webSocket,
			StompFrameListener listener,
			String tokenMarker
		) {
			this.webSocket = webSocket;
			this.listener = listener;
			this.tokenMarker = tokenMarker;
		}

		void subscribe(String subscriptionId) throws Exception {
			send(new StringBuilder()
					.append("SUBSCRIBE\n")
					.append("id:").append(subscriptionId).append('\n')
					.append("destination:")
					.append(USER_ERROR_DESTINATION)
					.append('\n')
					.append("ack:auto\n\n")
					.append('\0')
					.toString());
		}

		/** 개인 오류 destination의 실제 구독 완료 이벤트를 수집합니다. */
		static class SubscriptionCollector
				implements ApplicationListener<SessionSubscribeEvent> {

			private final LinkedBlockingQueue<String> sessionIds =
					new LinkedBlockingQueue<>();

			@Override
			public void onApplicationEvent(SessionSubscribeEvent event) {
				StompHeaderAccessor accessor =
						StompHeaderAccessor.wrap(event.getMessage());

				if (USER_ERROR_DESTINATION.equals(
						accessor.getDestination()
				)) {
					sessionIds.offer(accessor.getSessionId());
				}
			}

			void awaitSubscriptions(int expectedCount) throws Exception {
				for (int index = 0; index < expectedCount; index++) {
					String sessionId = sessionIds.poll(
							5,
							TimeUnit.SECONDS
					);

					if (sessionId == null) {
						throw new IllegalStateException(
								"개인 오류 채널 구독이 완료되지 않았습니다."
						);
					}
				}
			}
		}

		void sendErrorRequest(String receiptId) throws Exception {
			send(new StringBuilder()
				.append("SEND\n")
				.append("destination:").append(TEST_SEND_DESTINATION).append('\n')
				.append("receipt:").append(receiptId).append("\n\n")
				.append("trigger")
				.append('\0')
				.toString());
		}

		String awaitMessage() throws Exception {
			return listener.awaitCommand("MESSAGE", Duration.ofSeconds(5));
		}

		String pollMessage(Duration timeout) throws Exception {
			return listener.pollCommand("MESSAGE", timeout);
		}

		String tokenMarker() {
			return tokenMarker;
		}

		void send(String frame) throws Exception {
			webSocket.sendText(frame, true).get(5, TimeUnit.SECONDS);
		}

		@Override
		public void close() {
			webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "test-complete");
		}
	}

	/** WebSocket callback을 여러 개의 완성된 STOMP frame으로 분리합니다. */
	private static class StompFrameListener implements WebSocket.Listener {

		private final StringBuilder pending = new StringBuilder();
		private final LinkedBlockingQueue<String> frames = new LinkedBlockingQueue<>();

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
			String frame = pollCommand(command, timeout);
			if (frame == null) {
				throw new IllegalStateException(
					command + " frame을 제한 시간 안에 받지 못했습니다."
				);
			}
			return frame;
		}

		String pollCommand(String command, Duration timeout) throws Exception {
			long deadline = System.nanoTime() + timeout.toNanos();
			while (true) {
				long remaining = deadline - System.nanoTime();
				if (remaining <= 0) {
					return null;
				}
				String frame = frames.poll(remaining, TimeUnit.NANOSECONDS);
				if (frame == null) {
					return null;
				}
				if (frame.stripLeading().startsWith(command + "\n")) {
					return frame.stripLeading();
				}
			}
		}
	}
}
