package com.ssafy.bidlive.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import javax.crypto.SecretKey;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.bidlive.common.config.WebSocketProperties;
import com.ssafy.bidlive.member.application.MemberPersistence;
import com.ssafy.bidlive.member.domain.Member;
import com.ssafy.bidlive.member.domain.MemberRole;
import com.ssafy.bidlive.member.domain.MemberStatus;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/** 실제 STOMP 연결에서 무중단 토큰 재인증의 성공·실패·동시 갱신 경로를 검증합니다. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(StompTokenRenewalIntegrationTest.RenewalObserverTestConfig.class)
class StompTokenRenewalIntegrationTest {

	private static final long ACTIVE_MEMBER_ID = 301L;
	private static final long OTHER_MEMBER_ID = 302L;
	private static final long SUSPENDED_MEMBER_ID = 303L;
	private static final String RENEW_DESTINATION = "/app/auth/renew";
	private static final String ERROR_DESTINATION = "/user/queue/errors";
	private static final String FORGED_KEY_MARKER = "forged-renewal-key-marker";

	@LocalServerPort
	private int port;

	@Autowired
	private JwtProperties jwtProperties;

	@Autowired
	private WebSocketProperties webSocketProperties;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private RenewalSessionCollector sessionCollector;

	@MockitoBean
	private MemberPersistence memberPersistence;

	private Instant connectExpiresAt;
	private Instant laterExpiresAt;
	private Instant latestExpiresAt;

	@BeforeEach
	void setUp() {
		sessionCollector.clear();
		Instant baseTime = Instant.now()
			.plusSeconds(60)
			.truncatedTo(ChronoUnit.SECONDS);
		connectExpiresAt = baseTime.plus(Duration.ofMinutes(10));
		laterExpiresAt = baseTime.plus(Duration.ofHours(1));
		latestExpiresAt = baseTime.plus(Duration.ofHours(2));

		Member activeMember = member(MemberStatus.ACTIVE, MemberRole.USER);
		Member otherMember = member(MemberStatus.ACTIVE, MemberRole.USER);
		Member suspendedMember = member(MemberStatus.SUSPENDED, MemberRole.ADMIN);

		when(memberPersistence.findById(ACTIVE_MEMBER_ID)).thenReturn(
			Optional.of(activeMember)
		);
		when(memberPersistence.findById(OTHER_MEMBER_ID)).thenReturn(
			Optional.of(otherMember)
		);
		when(memberPersistence.findById(SUSPENDED_MEMBER_ID)).thenReturn(
			Optional.of(suspendedMember)
		);
	}

	@Test
	void renewsActiveSessionWithoutReplacingSessionPrincipalOrSubscription()
		throws Exception {
		try (TestStompClient client = connect(ACTIVE_MEMBER_ID, connectExpiresAt)) {
			client.subscribeErrors("active-errors");
			SessionIdentity subscribedIdentity = sessionCollector.awaitSubscription();
			String renewalToken = accessToken(ACTIVE_MEMBER_ID, laterExpiresAt);

			client.sendRenewal("renew-active", renewalToken);
			client.awaitReceipt("renew-active");
			ObservedRenewal observedRenewal = sessionCollector.awaitRenewal();
			awaitExpiration(observedRenewal.sessionAttributes(), laterExpiresAt);

			assertThat(observedRenewal.sessionId())
				.isEqualTo(subscribedIdentity.sessionId());
			assertThat(observedRenewal.principal())
				.isSameAs(subscribedIdentity.principal());
			assertThat(observedRenewal.principal().memberId())
				.isEqualTo(ACTIVE_MEMBER_ID);

			String otherMemberToken = accessToken(
				OTHER_MEMBER_ID,
				latestExpiresAt
			);
			client.sendRenewal("renew-other-member", otherMemberToken);

			assertPersonalError(
				client.awaitMessage(),
				StompTokenRenewalErrorCode.AUTH_PRINCIPAL_MISMATCH,
				"renew-other-member",
				otherMemberToken
			);
			assertThat(currentExpiration(observedRenewal.sessionAttributes()))
				.isEqualTo(laterExpiresAt);
		}
	}

	@Test
	void renewsSuspendedSessionWithoutReplacingDatabasePrincipalRole()
		throws Exception {
		try (TestStompClient client = connect(SUSPENDED_MEMBER_ID, connectExpiresAt)) {
			client.subscribeErrors("suspended-errors");
			SessionIdentity subscribedIdentity = sessionCollector.awaitSubscription();

			client.sendRenewal(
				"renew-suspended",
				accessToken(SUSPENDED_MEMBER_ID, laterExpiresAt)
			);
			client.awaitReceipt("renew-suspended");
			ObservedRenewal observedRenewal = sessionCollector.awaitRenewal();
			awaitExpiration(observedRenewal.sessionAttributes(), laterExpiresAt);

			assertThat(observedRenewal.principal())
				.isSameAs(subscribedIdentity.principal());
			assertThat(observedRenewal.principal().role())
				.isEqualTo(MemberRole.ADMIN);
		}
	}

	@Test
	void publishesExpiredAndInvalidErrorsWhileKeepingConnectionOpen()
		throws Exception {
		try (TestStompClient client = connect(ACTIVE_MEMBER_ID, connectExpiresAt)) {
			client.subscribeErrors("failure-errors");
			sessionCollector.awaitSubscription();

			String expiredToken = accessToken(
				ACTIVE_MEMBER_ID,
				Instant.now().minusSeconds(60)
			);
			client.sendRenewal("renew-expired", expiredToken);
			assertPersonalError(
				client.awaitMessage(),
				StompTokenRenewalErrorCode.AUTH_RENEW_EXPIRED,
				"renew-expired",
				expiredToken
			);

			SecretKey forgedKey = Keys.hmacShaKeyFor(
				FORGED_KEY_MARKER.repeat(3).getBytes(StandardCharsets.UTF_8)
			);
			String forgedToken = token(
				ACTIVE_MEMBER_ID,
				"ACCESS",
				laterExpiresAt,
				forgedKey
			);
			client.sendRenewal("renew-forged", forgedToken);
			assertPersonalError(
				client.awaitMessage(),
				StompTokenRenewalErrorCode.AUTH_RENEW_INVALID,
				"renew-forged",
				forgedToken
			);

			client.sendRenewal(
				"renew-after-errors",
				accessToken(ACTIVE_MEMBER_ID, laterExpiresAt)
			);
			client.awaitReceipt("renew-after-errors");
		}
	}

	@Test
	void keepsLatestExpirationWhenRenewalRequestsOverlap() throws Exception {
		try (TestStompClient client = connect(ACTIVE_MEMBER_ID, connectExpiresAt)) {
			client.subscribeErrors("overlap-errors");
			SessionIdentity subscribedIdentity = sessionCollector.awaitSubscription();

			client.sendRenewal(
				"renew-latest",
				accessToken(ACTIVE_MEMBER_ID, latestExpiresAt)
			);
			client.sendRenewal(
				"renew-later",
				accessToken(ACTIVE_MEMBER_ID, laterExpiresAt)
			);
			client.awaitReceipts(Set.of("renew-latest", "renew-later"));
			ObservedRenewal firstRenewal = sessionCollector.awaitRenewal();
			ObservedRenewal secondRenewal = sessionCollector.awaitRenewal();
			awaitExpiration(secondRenewal.sessionAttributes(), latestExpiresAt);

			assertThat(firstRenewal.sessionId())
				.isEqualTo(subscribedIdentity.sessionId());
			assertThat(secondRenewal.sessionId())
				.isEqualTo(subscribedIdentity.sessionId());
			assertThat(firstRenewal.sessionAttributes())
				.isSameAs(secondRenewal.sessionAttributes());
			assertThat(currentExpiration(secondRenewal.sessionAttributes()))
				.isEqualTo(latestExpiresAt);
		}
	}

	private TestStompClient connect(long memberId, Instant expiresAt)
		throws Exception {
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
		client.send(connectFrame(accessToken(memberId, expiresAt)));
		assertThat(listener.awaitCommand("CONNECTED", Duration.ofSeconds(5)))
			.startsWith("CONNECTED\n");
		return client;
	}

	private URI webSocketUri() {
		return URI.create(
			"ws://127.0.0.1:" + port + webSocketProperties.getEndpoint()
		);
	}

	private String connectFrame(String accessToken) {
		return new StringBuilder()
			.append("CONNECT\n")
			.append("accept-version:1.2\n")
			.append("heart-beat:0,0\n")
			.append("Authorization:Bearer ").append(accessToken)
			.append("\n\n")
			.append('\0')
			.toString();
	}

	private String accessToken(long memberId, Instant expiresAt) {
		return token(
			memberId,
			"ACCESS",
			expiresAt,
			jwtProperties.signingKey()
		);
	}

	private String token(
		long memberId,
		String tokenType,
		Instant expiresAt,
		SecretKey signingKey
	) {
		return Jwts.builder()
			.issuer(jwtProperties.issuer())
			.subject(Long.toString(memberId))
			.expiration(Date.from(expiresAt))
			.claim("tokenType", tokenType)
			.claim("role", MemberRole.USER.name())
			.signWith(signingKey, Jwts.SIG.HS256)
			.compact();
	}

	private Member member(MemberStatus status, MemberRole role) {
		Member member = mock(Member.class);
		when(member.getStatus()).thenReturn(status);
		when(member.getRole()).thenReturn(role);
		return member;
	}

	private void assertPersonalError(
		String frame,
		StompTokenRenewalErrorCode expectedCode,
		String correlationId,
		String hiddenToken
	) throws Exception {
		JsonNode json = objectMapper.readTree(body(frame));

		assertThat(json.get("type").asText()).isEqualTo("ERROR");
		assertThat(json.get("data").get("code").asText())
			.isEqualTo(expectedCode.code());
		assertThat(json.get("data").get("message").asText())
			.isEqualTo(expectedCode.publicMessage());
		assertThat(json.get("data").get("refDestination").asText())
			.isEqualTo(RENEW_DESTINATION);
		assertThat(json.get("data").get("correlationId").asText())
			.isEqualTo(correlationId);
		assertThat(body(frame))
			.doesNotContain(hiddenToken)
			.doesNotContain(Long.toString(ACTIVE_MEMBER_ID))
			.doesNotContain(Long.toString(OTHER_MEMBER_ID))
			.doesNotContain("StompTokenRenewalException")
			.doesNotContain("AccessTokenVerificationException")
			.doesNotContain(MemberStatus.ACTIVE.name())
			.doesNotContain(MemberStatus.SUSPENDED.name())
			.doesNotContain(MemberStatus.WITHDRAWN.name());
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

	private void awaitExpiration(
		Map<String, Object> sessionAttributes,
		Instant expectedExpiration
	) throws Exception {
		long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
		while (System.nanoTime() < deadline) {
			if (expectedExpiration.equals(currentExpiration(sessionAttributes))) {
				return;
			}
			Thread.sleep(10);
		}
		assertThat(currentExpiration(sessionAttributes))
			.isEqualTo(expectedExpiration);
	}

	private Instant currentExpiration(Map<String, Object> sessionAttributes) {
		synchronized (sessionAttributes) {
			return (Instant) sessionAttributes.get(
				WebSocketSessionAttributes.TOKEN_EXPIRES_AT
			);
		}
	}

	/** 테스트에서 구독 시점과 재인증 요청 시점의 동일 세션 상태를 관찰합니다. */
	static class RenewalSessionCollector
		implements ChannelInterceptor, ApplicationListener<SessionSubscribeEvent> {

		private final LinkedBlockingQueue<SessionIdentity> subscriptions =
			new LinkedBlockingQueue<>();
		private final LinkedBlockingQueue<ObservedRenewal> renewals =
			new LinkedBlockingQueue<>();

		@Override
		public Message<?> preSend(Message<?> message, MessageChannel channel) {
			StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(
				message,
				StompHeaderAccessor.class
			);
			if (accessor == null
				|| accessor.getCommand() != StompCommand.SEND
				|| !RENEW_DESTINATION.equals(accessor.getDestination())
				|| !(accessor.getUser() instanceof SecurityPrincipal principal)
				|| accessor.getSessionAttributes() == null) {
				return message;
			}

			renewals.offer(new ObservedRenewal(
				accessor.getSessionId(),
				principal,
				accessor.getSessionAttributes()
			));
			return message;
		}

		@Override
		public void onApplicationEvent(SessionSubscribeEvent event) {
			StompHeaderAccessor accessor = StompHeaderAccessor.wrap(
				event.getMessage()
			);
			Principal principal = event.getUser();
			if (ERROR_DESTINATION.equals(accessor.getDestination())
				&& principal instanceof SecurityPrincipal securityPrincipal) {
				subscriptions.offer(new SessionIdentity(
					accessor.getSessionId(),
					securityPrincipal
				));
			}
		}

		SessionIdentity awaitSubscription() throws Exception {
			SessionIdentity identity = subscriptions.poll(5, TimeUnit.SECONDS);
			if (identity == null) {
				throw new IllegalStateException(
					"개인 오류 구독 완료를 확인하지 못했습니다."
				);
			}
			return identity;
		}

		ObservedRenewal awaitRenewal() throws Exception {
			ObservedRenewal renewal = renewals.poll(5, TimeUnit.SECONDS);
			if (renewal == null) {
				throw new IllegalStateException(
					"재인증 요청 세션을 확인하지 못했습니다."
				);
			}
			return renewal;
		}

		void clear() {
			subscriptions.clear();
			renewals.clear();
		}
	}

	/** 통합 테스트에서 세션 상태를 관찰할 inbound interceptor를 등록합니다. */
	@TestConfiguration
	static class RenewalObserverTestConfig
		implements WebSocketMessageBrokerConfigurer {

		private final RenewalSessionCollector collector =
			new RenewalSessionCollector();

		@Bean
		RenewalSessionCollector renewalSessionCollector() {
			return collector;
		}

		@Override
		public void configureClientInboundChannel(
			ChannelRegistration registration
		) {
			registration.interceptors(collector);
		}
	}

	/** 오류 구독 시점의 서버 세션과 Principal을 표현합니다. */
	private record SessionIdentity(
		String sessionId,
		SecurityPrincipal principal
	) {
	}

	/** 재인증 SEND frame이 공유하는 서버 세션 속성 참조를 표현합니다. */
	private record ObservedRenewal(
		String sessionId,
		SecurityPrincipal principal,
		Map<String, Object> sessionAttributes
	) {
	}

	/** 실제 WebSocket 연결에 STOMP frame을 전송하고 응답을 구분해 기다립니다. */
	private static class TestStompClient implements AutoCloseable {

		private final WebSocket webSocket;
		private final StompFrameListener listener;

		TestStompClient(WebSocket webSocket, StompFrameListener listener) {
			this.webSocket = webSocket;
			this.listener = listener;
		}

		void subscribeErrors(String subscriptionId) throws Exception {
			send(new StringBuilder()
				.append("SUBSCRIBE\n")
				.append("id:").append(subscriptionId).append('\n')
				.append("destination:").append(ERROR_DESTINATION).append('\n')
				.append("ack:auto\n\n")
				.append('\0')
				.toString());
		}

		void sendRenewal(String receiptId, String accessToken) throws Exception {
			String body = "{\"accessToken\":\"" + accessToken + "\"}";
			send(new StringBuilder()
				.append("SEND\n")
				.append("destination:").append(RENEW_DESTINATION).append('\n')
				.append("content-type:application/json\n")
				.append("receipt:").append(receiptId).append('\n')
				.append("content-length:")
				.append(body.getBytes(StandardCharsets.UTF_8).length)
				.append("\n\n")
				.append(body)
				.append('\0')
				.toString());
		}

		void awaitReceipt(String receiptId) throws Exception {
			listener.awaitReceipt(receiptId, Duration.ofSeconds(5));
		}

		void awaitReceipts(Set<String> receiptIds) throws Exception {
			listener.awaitReceipts(receiptIds, Duration.ofSeconds(5));
		}

		String awaitMessage() throws Exception {
			return listener.awaitCommand("MESSAGE", Duration.ofSeconds(5));
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
		private volatile Throwable error;

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

		@Override
		public void onError(WebSocket webSocket, Throwable failure) {
			error = failure;
		}

		String awaitCommand(String command, Duration timeout) throws Exception {
			long deadline = System.nanoTime() + timeout.toNanos();
			while (System.nanoTime() < deadline) {
				String frame = pollFrame(deadline);
				if (frame == null) {
					break;
				}
				String normalized = frame.stripLeading();
				if (normalized.startsWith(command + "\n")) {
					return normalized;
				}
			}
			throw unavailableFrame(command);
		}

		void awaitReceipt(String receiptId, Duration timeout) throws Exception {
			awaitReceipts(Set.of(receiptId), timeout);
		}

		void awaitReceipts(Set<String> receiptIds, Duration timeout)
			throws Exception {
			Set<String> remaining = new HashSet<>(receiptIds);
			long deadline = System.nanoTime() + timeout.toNanos();
			while (!remaining.isEmpty() && System.nanoTime() < deadline) {
				String frame = pollFrame(deadline);
				if (frame == null) {
					break;
				}
				String normalized = frame.stripLeading();
				if (normalized.startsWith("RECEIPT\n")) {
					remaining.remove(header(normalized, "receipt-id"));
				}
			}
			if (!remaining.isEmpty()) {
				throw unavailableFrame("RECEIPT " + remaining);
			}
		}

		private String pollFrame(long deadline) throws Exception {
			long remaining = deadline - System.nanoTime();
			if (remaining <= 0) {
				return null;
			}
			String frame = frames.poll(remaining, TimeUnit.NANOSECONDS);
			if (frame == null && error != null) {
				throw new IllegalStateException("WebSocket 수신에 실패했습니다.", error);
			}
			return frame;
		}

		private String header(String frame, String name) {
			String prefix = name + ":";
			for (String line : frame.split("\n")) {
				if (line.startsWith(prefix)) {
					return line.substring(prefix.length());
				}
			}
			return null;
		}

		private IllegalStateException unavailableFrame(String description) {
			if (error != null) {
				return new IllegalStateException(
					"WebSocket 수신에 실패했습니다.",
					error
				);
			}
			return new IllegalStateException(
				description + " frame을 제한 시간 안에 받지 못했습니다."
			);
		}
	}
}
