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
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import javax.crypto.SecretKey;

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
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.socket.messaging.SessionConnectedEvent;

/** 실제 STOMP handshake에서 회원 상태별 CONNECT와 안전한 인증 ERROR frame을 검증합니다. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(StompConnectIntegrationTest.ConnectedPrincipalTestConfig.class)
class StompConnectIntegrationTest {

	private static final long ACTIVE_MEMBER_ID = 101L;
	private static final long SUSPENDED_MEMBER_ID = 102L;
	private static final long WITHDRAWN_MEMBER_ID = 103L;
	private static final long MISSING_MEMBER_ID = 104L;
	private static final String TEST_TOKEN_MARKER = "test-token-must-not-be-exposed";

	@LocalServerPort
	private int port;

	@Autowired
	private JwtProperties jwtProperties;

	@Autowired
	private WebSocketProperties webSocketProperties;

	@Autowired
	private ConnectedPrincipalCollector principalCollector;

	@MockitoBean
	private MemberPersistence memberPersistence;

	@BeforeEach
	void setUpMembers() {
		principalCollector.clear();

		Member activeMember = member(
				MemberStatus.ACTIVE,
				MemberRole.USER
		);
		Member suspendedMember = member(
				MemberStatus.SUSPENDED,
				MemberRole.ADMIN
		);
		Member withdrawnMember = member(
				MemberStatus.WITHDRAWN,
				MemberRole.USER
		);

		when(memberPersistence.findById(ACTIVE_MEMBER_ID))
				.thenReturn(Optional.of(activeMember));
		when(memberPersistence.findById(SUSPENDED_MEMBER_ID))
				.thenReturn(Optional.of(suspendedMember));
		when(memberPersistence.findById(WITHDRAWN_MEMBER_ID))
				.thenReturn(Optional.of(withdrawnMember));
		when(memberPersistence.findById(MISSING_MEMBER_ID))
				.thenReturn(Optional.empty());
	}

	@Test
	void connectsActiveMemberWithServerVerifiedPrincipalAndHeartbeat() throws Exception {
		String response = connect(List.of("Bearer " + accessToken(ACTIVE_MEMBER_ID)));

		assertThat(response).startsWith("CONNECTED\n");
		assertThat(response).contains("heart-beat:10000,10000");
		assertThat(principalCollector.awaitName()).isEqualTo(Long.toString(ACTIVE_MEMBER_ID));
	}

	@Test
	void connectsSuspendedMemberWithServerVerifiedPrincipal() throws Exception {
		String response = connect(List.of("Bearer " + accessToken(SUSPENDED_MEMBER_ID)));

		assertThat(response).startsWith("CONNECTED\n");
		assertThat(principalCollector.awaitName()).isEqualTo(Long.toString(SUSPENDED_MEMBER_ID));
	}

	@Test
	void rejectsWithdrawnMemberAsInvalidWithoutSensitiveDetails() throws Exception {
		String token = accessToken(WITHDRAWN_MEMBER_ID);

		assertError(
			connect(List.of("Bearer " + token)),
			"AUTH_INVALID",
			token,
			"WITHDRAWN",
			Long.toString(WITHDRAWN_MEMBER_ID)
		);
	}

	@Test
	void rejectsMissingMemberAsInvalidWithoutExposingMemberId() throws Exception {
		String token = accessToken(MISSING_MEMBER_ID);

		assertError(
			connect(List.of("Bearer " + token)),
			"AUTH_INVALID",
			token,
			Long.toString(MISSING_MEMBER_ID)
		);
	}

	@Test
	void rejectsExpiredTokenAsExpired() throws Exception {
		String token = token(
			ACTIVE_MEMBER_ID,
			"ACCESS",
			Instant.now().minusSeconds(1),
			jwtProperties.signingKey()
		);

		assertError(connect(List.of("Bearer " + token)), "AUTH_EXPIRED", token);
	}

	@Test
	void rejectsForgedTokenAsInvalid() throws Exception {
		SecretKey forgedKey = Keys.hmacShaKeyFor(TEST_TOKEN_MARKER.repeat(3).getBytes(StandardCharsets.UTF_8));
		String token = token(
			ACTIVE_MEMBER_ID,
			"ACCESS",
			Instant.now().plus(Duration.ofMinutes(30)),
			forgedKey
		);

		assertError(connect(List.of("Bearer " + token)), "AUTH_INVALID", token);
	}

	@Test
	void rejectsRefreshTokenAsInvalid() throws Exception {
		String token = token(
			ACTIVE_MEMBER_ID,
			"REFRESH",
			Instant.now().plus(Duration.ofDays(1)),
			jwtProperties.signingKey()
		);

		assertError(connect(List.of("Bearer " + token)), "AUTH_INVALID", token);
	}

	@Test
	void rejectsMissingAuthorizationAsInvalid() throws Exception {
		assertError(connect(List.of()), "AUTH_INVALID");
	}

	@Test
	void rejectsDuplicateAuthorizationAsInvalidWithoutExposingEitherToken() throws Exception {
		String firstToken = accessToken(ACTIVE_MEMBER_ID);
		String secondToken = accessToken(SUSPENDED_MEMBER_ID);

		assertError(
			connect(List.of("Bearer " + firstToken, "Bearer " + secondToken)),
			"AUTH_INVALID",
			firstToken,
			secondToken
		);
	}

	@Test
	void ignoresClientSuppliedIdentityHeadersAndUsesVerifiedTokenPrincipal() throws Exception {
		String response = connect(
			List.of("Bearer " + accessToken(ACTIVE_MEMBER_ID)),
			List.of(
				"memberId:999999",
				"user:999999",
				"simpUser:999999"
			)
		);

		assertThat(response).startsWith("CONNECTED\n");
		assertThat(principalCollector.awaitName())
			.isEqualTo(Long.toString(ACTIVE_MEMBER_ID));
	}

	private String connect(List<String> authorizationHeaders) throws Exception {
		return connect(authorizationHeaders, List.of());
	}

	private String connect(
		List<String> authorizationHeaders,
		List<String> additionalHeaders
	) throws Exception {
		StompFrameListener listener = new StompFrameListener();
		WebSocket webSocket = HttpClient.newHttpClient()
			.newWebSocketBuilder()
			.header("Origin", webSocketProperties.getAllowedOrigins().getFirst())
			.subprotocols("v12.stomp")
			.buildAsync(webSocketUri(), listener)
			.get(5, TimeUnit.SECONDS);
		try {
			webSocket.sendText(
				connectFrame(authorizationHeaders, additionalHeaders),
				true
			)
				.get(5, TimeUnit.SECONDS);
			return listener.awaitFrame();
		} finally {
			webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "test-complete");
		}
	}

	private URI webSocketUri() {
		return URI.create("ws://127.0.0.1:" + port + webSocketProperties.getEndpoint());
	}

	private String connectFrame(
		List<String> authorizationHeaders,
		List<String> additionalHeaders
	) {
		StringBuilder frame = new StringBuilder()
			.append("CONNECT\n")
			.append("accept-version:1.2\n")
			.append("heart-beat:10000,10000\n");
		authorizationHeaders.forEach(value -> frame
			.append("Authorization:")
			.append(value)
			.append('\n'));
		additionalHeaders.forEach(value -> frame
			.append(value)
			.append('\n'));
		return frame.append('\n').append('\0').toString();
	}

	private String accessToken(long memberId) {
		return token(
			memberId,
			"ACCESS",
			Instant.now().plus(Duration.ofMinutes(30)),
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

	private void assertError(String response, String code, String... hiddenValues) {
		assertThat(response).startsWith("ERROR\n");
		assertThat(response).contains("\"type\":\"ERROR\"");
		assertThat(response).contains("\"code\":\"" + code + "\"");

		String responseBody = response.substring(
				response.indexOf("\n\n") + 2
		);

		for (String hiddenValue : hiddenValues) {
			assertThat(responseBody).doesNotContain(hiddenValue);
		}

		assertThat(responseBody)
				.doesNotContain("StompAuthenticationException")
				.doesNotContain("AccessTokenVerificationException");

		assertThat(principalCollector.hasPrincipal()).isFalse();
	}

	/** 서버의 STOMP CONNECTED 이벤트에서 검증된 Principal 이름을 수집합니다. */
	static class ConnectedPrincipalCollector implements ApplicationListener<SessionConnectedEvent> {

		private final LinkedBlockingQueue<String> principalNames = new LinkedBlockingQueue<>();

		@Override
		public void onApplicationEvent(SessionConnectedEvent event) {
			Principal user = event.getUser();
			if (user != null) {
				principalNames.offer(user.getName());
			}
		}

		String awaitName() throws Exception {
			String principalName = principalNames.poll(5, TimeUnit.SECONDS);
			if (principalName == null) {
				throw new IllegalStateException("CONNECTED event did not contain a Principal");
			}
			return principalName;
		}

		void clear() {
			principalNames.clear();
		}

		boolean hasPrincipal() {
			return !principalNames.isEmpty();
		}
	}

	/** 통합 테스트에서 STOMP 연결 이벤트의 Principal을 관찰하는 Bean을 제공합니다. */
	@TestConfiguration
	static class ConnectedPrincipalTestConfig {

		@Bean
		ConnectedPrincipalCollector connectedPrincipalCollector() {
			return new ConnectedPrincipalCollector();
		}
	}

	/** Java WebSocket callback을 하나의 완성된 STOMP frame으로 조립합니다. */
	private static class StompFrameListener implements WebSocket.Listener {

		private final StringBuilder frame = new StringBuilder();
		private final CompletableFuture<String> completedFrame = new CompletableFuture<>();

		@Override
		public void onOpen(WebSocket webSocket) {
			webSocket.request(1);
		}

		@Override
		public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
			frame.append(data);
			if (last && frame.indexOf("\0") >= 0) {
				completedFrame.complete(frame.toString());
			}
			webSocket.request(1);
			return null;
		}

		@Override
		public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
			webSocket.request(1);
			return null;
		}

		@Override
		public void onError(WebSocket webSocket, Throwable error) {
			completedFrame.completeExceptionally(error);
		}

		String awaitFrame() throws Exception {
			return completedFrame.get(5, TimeUnit.SECONDS);
		}
	}
}
