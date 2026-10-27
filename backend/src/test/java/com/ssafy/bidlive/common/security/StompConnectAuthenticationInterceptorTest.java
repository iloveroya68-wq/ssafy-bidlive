package com.ssafy.bidlive.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

import com.ssafy.bidlive.member.domain.MemberRole;
import com.ssafy.bidlive.common.observability.RealtimeObservationMetrics;
import com.ssafy.bidlive.common.observability.RealtimeObservationMetrics.ConnectFailure;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;

/** STOMP CONNECT 인증 성공·실패에 따른 Principal과 세션 속성 변경을 검증합니다. */
@ExtendWith(MockitoExtension.class)
class StompConnectAuthenticationInterceptorTest {

	private static final String TOKEN = "header.payload.signature";
	private static final Instant CONNECTED_AT = Instant.parse("2026-07-28T02:00:00Z");
	private static final Instant EXPIRES_AT = Instant.parse("2026-07-28T03:00:00Z");

	@Mock
	private StompBearerTokenResolver bearerTokenResolver;

	@Mock
	private StompConnectAuthenticationService authenticationService;

	@Mock
	private MessageChannel channel;

	@Mock
	private RealtimeObservationMetrics metrics;

	private StompConnectAuthenticationInterceptor interceptor;

	@BeforeEach
	void setUp() {
		interceptor = new StompConnectAuthenticationInterceptor(
			bearerTokenResolver,
			authenticationService,
			metrics,
			Clock.fixed(CONNECTED_AT, ZoneOffset.UTC)
		);
	}

	@Test
	void storesPrincipalAndSessionTimesAfterSuccessfulConnectAuthentication() {
		SecurityPrincipal principal = new SecurityPrincipal(25L, MemberRole.USER);
		StompAuthenticationResult authentication = new StompAuthenticationResult(
			principal,
			EXPIRES_AT
		);
		when(bearerTokenResolver.resolve(any())).thenReturn(TOKEN);
		when(authenticationService.authenticate(TOKEN)).thenReturn(authentication);
		Map<String, Object> sessionAttributes = new HashMap<>();
		Message<byte[]> message = message(StompCommand.CONNECT, sessionAttributes, "memberId=999");

		Message<?> returned = interceptor.preSend(message, channel);

		StompHeaderAccessor accessor = accessor(returned);
		assertThat(returned).isSameAs(message);
		assertThat(accessor.getUser()).isSameAs(principal);
		assertThat(sessionAttributes)
			.containsEntry(WebSocketSessionAttributes.TOKEN_EXPIRES_AT, EXPIRES_AT)
			.containsEntry(WebSocketSessionAttributes.CONNECTED_AT, CONNECTED_AT)
			.doesNotContainKey("memberId");
		verify(authenticationService).authenticate(TOKEN);
	}

	@ParameterizedTest
	@EnumSource(value = StompCommand.class, names = {"SEND", "SUBSCRIBE"})
	void passesNonConnectFramesWithoutAuthentication(StompCommand command) {
		Map<String, Object> sessionAttributes = new HashMap<>();
		Message<byte[]> message = message(command, sessionAttributes, "memberId=999");

		Message<?> returned = interceptor.preSend(message, channel);

		assertThat(returned).isSameAs(message);
		assertThat(accessor(returned).getUser()).isNull();
		assertThat(sessionAttributes).isEmpty();
		verifyNoInteractions(bearerTokenResolver, authenticationService);
	}

	@Test
	void rejectsMissingSessionAttributesWithoutStoringPrincipal() {
		SecurityPrincipal principal = new SecurityPrincipal(25L, MemberRole.USER);
		when(bearerTokenResolver.resolve(any())).thenReturn(TOKEN);
		when(authenticationService.authenticate(TOKEN)).thenReturn(
			new StompAuthenticationResult(principal, EXPIRES_AT)
		);
		Message<byte[]> message = messageWithoutSessionAttributes(StompCommand.CONNECT);

		assertInvalid(() -> interceptor.preSend(message, channel));

		assertThat(accessor(message).getUser()).isNull();
	}

	@Test
	void leavesAuthenticationStateEmptyWhenBearerResolutionFails() {
		when(bearerTokenResolver.resolve(any())).thenThrow(
			new StompAuthenticationException(StompAuthenticationErrorCode.AUTH_INVALID)
		);
		Map<String, Object> sessionAttributes = new HashMap<>();
		Message<byte[]> message = message(StompCommand.CONNECT, sessionAttributes, "memberId=999");

		assertInvalid(() -> interceptor.preSend(message, channel));

		assertThat(accessor(message).getUser()).isNull();
		assertThat(sessionAttributes).isEmpty();
		verifyNoInteractions(authenticationService);
		verify(metrics).recordConnectFailed(ConnectFailure.INVALID);
	}

	@Test
	void recordsExpiredAuthenticationWithoutExposingToken() {
		when(bearerTokenResolver.resolve(any())).thenReturn(TOKEN);
		when(authenticationService.authenticate(TOKEN)).thenThrow(
			new StompAuthenticationException(
				StompAuthenticationErrorCode.AUTH_EXPIRED
			)
		);
		Map<String, Object> sessionAttributes = new HashMap<>();
		Message<byte[]> message = message(
			StompCommand.CONNECT,
			sessionAttributes,
			TOKEN
		);

		assertThatThrownBy(() -> interceptor.preSend(message, channel))
			.isInstanceOfSatisfying(
				StompAuthenticationException.class,
				exception -> assertThat(exception.errorCode())
					.isEqualTo(StompAuthenticationErrorCode.AUTH_EXPIRED)
			);

		verify(metrics).recordConnectFailed(ConnectFailure.EXPIRED);
		assertThat(sessionAttributes).isEmpty();
	}

	@Test
	void leavesAuthenticationStateEmptyWhenMemberAuthenticationFails() {
		when(bearerTokenResolver.resolve(any())).thenReturn(TOKEN);
		when(authenticationService.authenticate(TOKEN)).thenThrow(
			new StompAuthenticationException(StompAuthenticationErrorCode.AUTH_INVALID)
		);
		Map<String, Object> sessionAttributes = new HashMap<>();
		Message<byte[]> message = message(StompCommand.CONNECT, sessionAttributes, "memberId=999");

		assertInvalid(() -> interceptor.preSend(message, channel));

		assertThat(accessor(message).getUser()).isNull();
		assertThat(sessionAttributes).isEmpty();
	}

	private Message<byte[]> message(
		StompCommand command,
		Map<String, Object> sessionAttributes,
		String body
	) {
		StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
		accessor.setSessionAttributes(sessionAttributes);
		accessor.setLeaveMutable(true);
		return MessageBuilder.createMessage(
			body.getBytes(StandardCharsets.UTF_8),
			accessor.getMessageHeaders()
		);
	}

	private Message<byte[]> messageWithoutSessionAttributes(StompCommand command) {
		StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
		accessor.setLeaveMutable(true);
		return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
	}

	private StompHeaderAccessor accessor(Message<?> message) {
		return MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
	}

	private void assertInvalid(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
		assertThatThrownBy(callable)
			.isInstanceOfSatisfying(
				StompAuthenticationException.class,
				exception -> assertThat(exception.errorCode())
					.isEqualTo(StompAuthenticationErrorCode.AUTH_INVALID)
			);
	}
}
