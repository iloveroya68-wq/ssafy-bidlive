package com.ssafy.bidlive.common.websocket.subscription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ssafy.bidlive.common.security.SecurityPrincipal;
import com.ssafy.bidlive.common.observability.RealtimeObservationMetrics;
import com.ssafy.bidlive.common.websocket.StompErrorContext;
import com.ssafy.bidlive.common.websocket.StompErrorContextResolver;
import com.ssafy.bidlive.common.websocket.UserWebSocketErrorPublisher;
import com.ssafy.bidlive.member.domain.MemberRole;
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

/** 구독 허용·거부와 개인 오류 발행 후 연결 유지 처리를 검증합니다. */
@ExtendWith(MockitoExtension.class)
class StompSubscriptionAuthorizationInterceptorTest {

	private static final String SESSION_ID = "session-123";
	private static final String DESTINATION =
		"/topic/auctions/42/questions";

	@Mock
	private StompSubscriptionAuthorizationService authorizationService;

	@Mock
	private StompErrorContextResolver errorContextResolver;

	@Mock
	private UserWebSocketErrorPublisher errorPublisher;

	@Mock
	private RealtimeObservationMetrics metrics;

	@Mock
	private MessageChannel channel;

	private StompSubscriptionAuthorizationInterceptor interceptor;
	private SecurityPrincipal principal;

	@BeforeEach
	void setUp() {
		interceptor = new StompSubscriptionAuthorizationInterceptor(
			authorizationService,
			metrics,
			errorContextResolver,
			errorPublisher
		);
		principal = new SecurityPrincipal(25L, MemberRole.USER);
	}

	@Test
	void passesAllowedSubscribeFrameUnchanged() {
		Message<byte[]> message = stompMessage(
			StompCommand.SUBSCRIBE,
			principal,
			SESSION_ID,
			DESTINATION
		);
		when(authorizationService.canSubscribe(principal, DESTINATION))
			.thenReturn(true);

		Message<?> result = interceptor.preSend(message, channel);

		assertThat(result).isSameAs(message);
		verifyNoInteractions(errorContextResolver, errorPublisher);
	}

	@Test
	void dropsDeniedSubscribeAndPublishesPersonalError() {
		Message<byte[]> message = stompMessage(
			StompCommand.SUBSCRIBE,
			principal,
			SESSION_ID,
			DESTINATION
		);
		StompErrorContext errorContext = new StompErrorContext(
			principal,
			SESSION_ID,
			DESTINATION,
			null
		);
		when(authorizationService.canSubscribe(principal, DESTINATION))
			.thenReturn(false);
		when(errorContextResolver.resolve(message)).thenReturn(errorContext);

		Message<?> result = interceptor.preSend(message, channel);

		assertThat(result).isNull();
		verify(errorPublisher).publish(
			errorContext,
			StompSubscriptionErrorCode.SUBSCRIPTION_DENIED
		);
		verify(metrics).recordSubscriptionDenied();
	}

	@Test
	void dropsUnauthenticatedSubscribeWithoutPublishingPersonalError() {
		Message<byte[]> message = stompMessage(
			StompCommand.SUBSCRIBE,
			null,
			SESSION_ID,
			DESTINATION
		);
		when(authorizationService.canSubscribe(null, DESTINATION))
			.thenReturn(false);

		Message<?> result = interceptor.preSend(message, channel);

		assertThat(result).isNull();
		verify(metrics).recordSubscriptionDenied();
		verifyNoInteractions(errorContextResolver, errorPublisher);
	}

	@Test
	void dropsSubscribeWithoutSessionIdWithoutPublishingMalformedError() {
		Message<byte[]> message = stompMessage(
			StompCommand.SUBSCRIBE,
			principal,
			null,
			DESTINATION
		);
		when(authorizationService.canSubscribe(principal, DESTINATION))
			.thenReturn(false);

		Message<?> result = interceptor.preSend(message, channel);

		assertThat(result).isNull();
		verifyNoInteractions(errorContextResolver, errorPublisher);
	}

	@ParameterizedTest
	@EnumSource(
		value = StompCommand.class,
		names = {"CONNECT", "SEND", "UNSUBSCRIBE", "DISCONNECT"}
	)
	void passesNonSubscribeFramesUnchanged(StompCommand command) {
		Message<byte[]> message = stompMessage(
			command,
			principal,
			SESSION_ID,
			DESTINATION
		);

		Message<?> result = interceptor.preSend(message, channel);

		assertThat(result).isSameAs(message);
		verifyNoInteractions(
			authorizationService,
			errorContextResolver,
			errorPublisher
		);
	}

	@Test
	void exposesStableSubscriptionDeniedError() {
		assertThat(StompSubscriptionErrorCode.SUBSCRIPTION_DENIED.code())
			.isEqualTo("SUBSCRIPTION_DENIED");
		assertThat(
			StompSubscriptionErrorCode.SUBSCRIPTION_DENIED.publicMessage()
		).isEqualTo("해당 채널을 구독할 권한이 없습니다.");
	}

	private Message<byte[]> stompMessage(
		StompCommand command,
		SecurityPrincipal user,
		String sessionId,
		String destination
	) {
		StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
		accessor.setUser(user);
		accessor.setSessionId(sessionId);
		accessor.setDestination(destination);
		accessor.setLeaveMutable(true);
		return MessageBuilder.createMessage(
			new byte[0],
			accessor.getMessageHeaders()
		);
	}
}
