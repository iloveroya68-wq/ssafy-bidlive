package com.ssafy.bidlive.common.observability;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ExecutorChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/** STOMP frame마다 correlationId를 부여하고 payload 없는 구조화 처리 로그를 남깁니다. */
@Component
public class StompCorrelationInterceptor
	implements ExecutorChannelInterceptor {

	static final String CORRELATION_ID_HEADER = "bidliveCorrelationId";
	private static final String MDC_KEY = "correlationId";
	private static final String NATIVE_HEADER = "X-Correlation-ID";
	private static final Logger log = LoggerFactory.getLogger(
		StompCorrelationInterceptor.class
	);

	@Override
	public Message<?> preSend(Message<?> message, MessageChannel channel) {
		StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(
			message,
			StompHeaderAccessor.class
		);
		if (accessor == null || accessor.getCommand() == null) {
			return message;
		}

		String correlationId = resolveCorrelationId(accessor);
		accessor.setHeader(CORRELATION_ID_HEADER, correlationId);
		MDC.put(MDC_KEY, correlationId);
		return message;
	}

	@Override
	public void afterSendCompletion(
		Message<?> message,
		MessageChannel channel,
		boolean sent,
		Exception exception
	) {
		try {
			logFrame(message, sent, exception);
		} finally {
			MDC.remove(MDC_KEY);
		}
	}

	@Override
	public Message<?> beforeHandle(
		Message<?> message,
		MessageChannel channel,
		MessageHandler handler
	) {
		String correlationId = message.getHeaders().get(
			CORRELATION_ID_HEADER,
			String.class
		);
		if (correlationId != null) {
			MDC.put(MDC_KEY, correlationId);
		}
		return message;
	}

	@Override
	public void afterMessageHandled(
		Message<?> message,
		MessageChannel channel,
		MessageHandler handler,
		Exception exception
	) {
		MDC.remove(MDC_KEY);
	}

	private String resolveCorrelationId(StompHeaderAccessor accessor) {
		var values = accessor.getNativeHeader(NATIVE_HEADER);
		if (values != null && values.size() == 1) {
			try {
				return UUID.fromString(values.get(0)).toString();
			} catch (IllegalArgumentException ignored) {
				// 신뢰할 수 없는 외부 값은 로그 식별자로 사용하지 않고 새 UUID로 교체한다.
			}
		}
		return UUID.randomUUID().toString();
	}

	private void logFrame(
		Message<?> message,
		boolean sent,
		Exception exception
	) {
		StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(
			message,
			StompHeaderAccessor.class
		);
		if (accessor == null || accessor.getCommand() == null) {
			return;
		}
		String correlationId = message.getHeaders().get(
			CORRELATION_ID_HEADER,
			String.class
		);
		LoggingEventBuilder event = exception == null
			? log.atInfo()
			: log.atWarn();
		event.addKeyValue("correlationId", correlationId)
			.addKeyValue("stompSessionId", accessor.getSessionId())
			.addKeyValue("destination", accessor.getDestination())
			.addKeyValue("eventType", accessor.getCommand().name())
			.addKeyValue("outcome", sent ? "accepted" : "rejected");
		if (exception != null) {
			event.addKeyValue(
				"failureType",
				exception.getClass().getSimpleName()
			);
		}
		event.log("STOMP frame processed");
	}
}
