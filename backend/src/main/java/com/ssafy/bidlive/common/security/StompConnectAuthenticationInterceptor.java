package com.ssafy.bidlive.common.security;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

import com.ssafy.bidlive.common.observability.RealtimeObservationMetrics;
import com.ssafy.bidlive.common.observability.RealtimeObservationMetrics.ConnectFailure;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/** STOMP CONNECT를 인증하고 검증된 Principal과 만료 정보를 세션에 설정합니다. */
@Component
public class StompConnectAuthenticationInterceptor implements ChannelInterceptor {

	private final StompBearerTokenResolver bearerTokenResolver;
	private final StompConnectAuthenticationService authenticationService;
	private final RealtimeObservationMetrics metrics;
	private final Clock clock;

	@Autowired
	public StompConnectAuthenticationInterceptor(
		StompBearerTokenResolver bearerTokenResolver,
		StompConnectAuthenticationService authenticationService,
		RealtimeObservationMetrics metrics
	) {
		this(
			bearerTokenResolver,
			authenticationService,
			metrics,
			Clock.systemUTC()
		);
	}

	StompConnectAuthenticationInterceptor(
		StompBearerTokenResolver bearerTokenResolver,
		StompConnectAuthenticationService authenticationService,
		RealtimeObservationMetrics metrics,
		Clock clock
	) {
		this.bearerTokenResolver = bearerTokenResolver;
		this.authenticationService = authenticationService;
		this.metrics = metrics;
		this.clock = clock;
	}

	@Override
	public Message<?> preSend(Message<?> message, MessageChannel channel) {
		StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(
			message,
			StompHeaderAccessor.class
		);
		if (accessor == null || accessor.getCommand() != StompCommand.CONNECT) {
			return message;
		}

		try {
			String token = bearerTokenResolver.resolve(accessor);
			StompAuthenticationResult authentication =
				authenticationService.authenticate(token);
			Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
			if (sessionAttributes == null) {
				throw new StompAuthenticationException(
					StompAuthenticationErrorCode.AUTH_INVALID
				);
			}

			Instant connectedAt = clock.instant();
			accessor.setUser(authentication.principal());
			sessionAttributes.put(
				WebSocketSessionAttributes.TOKEN_EXPIRES_AT,
				authentication.tokenExpiresAt()
			);
			sessionAttributes.put(
				WebSocketSessionAttributes.CONNECTED_AT,
				connectedAt
			);
			return message;
		} catch (StompAuthenticationException exception) {
			metrics.recordConnectFailed(
				exception.errorCode() == StompAuthenticationErrorCode.AUTH_EXPIRED
					? ConnectFailure.EXPIRED
					: ConnectFailure.INVALID
			);
			throw exception;
		}
	}
}
