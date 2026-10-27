package com.ssafy.bidlive.common.websocket.subscription;

import com.ssafy.bidlive.common.security.SecurityPrincipal;
import com.ssafy.bidlive.common.observability.RealtimeObservationMetrics;
import com.ssafy.bidlive.common.websocket.StompErrorContextResolver;
import com.ssafy.bidlive.common.websocket.UserWebSocketErrorPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/** STOMP SUBSCRIBE 권한을 검사하고 거부된 구독 프레임만 폐기합니다. */
@Component
public class StompSubscriptionAuthorizationInterceptor
	implements ChannelInterceptor {

	private final StompSubscriptionAuthorizationService authorizationService;
	private final RealtimeObservationMetrics metrics;
	private final StompErrorContextResolver errorContextResolver;
	private final UserWebSocketErrorPublisher errorPublisher;

	public StompSubscriptionAuthorizationInterceptor(
		StompSubscriptionAuthorizationService authorizationService,
		RealtimeObservationMetrics metrics,
		StompErrorContextResolver errorContextResolver,
		@Lazy UserWebSocketErrorPublisher errorPublisher
	) {
		this.authorizationService = authorizationService;
		this.metrics = metrics;
		this.errorContextResolver = errorContextResolver;
		this.errorPublisher = errorPublisher;
	}

	@Override
	public Message<?> preSend(Message<?> message, MessageChannel channel) {
		StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(
			message,
			StompHeaderAccessor.class
		);
		if (accessor == null || accessor.getCommand() != StompCommand.SUBSCRIBE) {
			return message;
		}

		boolean allowed = authorizationService.canSubscribe(
			accessor.getUser(),
			accessor.getDestination()
		);
		if (allowed) {
			return message;
		}

		metrics.recordSubscriptionDenied();
		publishDenialIfPossible(message, accessor);
		return null;
	}

	private void publishDenialIfPossible(
		Message<?> message,
		StompHeaderAccessor accessor
	) {
		if (!(accessor.getUser() instanceof SecurityPrincipal)) {
			return;
		}
		if (!hasText(accessor.getSessionId())
			|| !hasText(accessor.getDestination())) {
			return;
		}

		errorPublisher.publish(
			errorContextResolver.resolve(message),
			StompSubscriptionErrorCode.SUBSCRIPTION_DENIED
		);
	}

	private boolean hasText(String value) {
		return value != null && !value.isBlank();
	}
}
