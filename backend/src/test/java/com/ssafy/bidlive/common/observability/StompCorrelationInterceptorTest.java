package com.ssafy.bidlive.common.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

/** STOMP correlationId 전파와 payload·Authorization 비노출 로그를 검증합니다. */
class StompCorrelationInterceptorTest {

	private StompCorrelationInterceptor interceptor;
	private MessageChannel channel;
	private Logger logger;
	private ListAppender<ILoggingEvent> appender;

	@BeforeEach
	void setUp() {
		interceptor = new StompCorrelationInterceptor();
		channel = mock(MessageChannel.class);
		logger = (Logger) LoggerFactory.getLogger(
			StompCorrelationInterceptor.class
		);
		appender = new ListAppender<>();
		appender.start();
		logger.addAppender(appender);
	}

	@AfterEach
	void tearDown() {
		logger.detachAppender(appender);
		appender.stop();
		MDC.clear();
	}

	@Test
	void propagatesValidCorrelationIdAndLogsNoSensitiveFrameData() {
		String correlationId = "85eb7fc1-b99c-4e81-b973-b1203ce91809";
		Message<byte[]> message = message(correlationId);

		Message<?> result = interceptor.preSend(message, channel);
		interceptor.afterSendCompletion(result, channel, true, null);

		assertThat(result.getHeaders().get(
			StompCorrelationInterceptor.CORRELATION_ID_HEADER
		)).isEqualTo(correlationId);
		assertThat(appender.list).hasSize(1);
		ILoggingEvent event = appender.list.get(0);
		assertThat(event.getFormattedMessage()).isEqualTo(
			"STOMP frame processed"
		);
		assertThat(event.getKeyValuePairs().toString())
			.contains("correlationId=\"" + correlationId + "\"")
			.doesNotContain(
				"header.payload.signature",
				"민감한 메시지 원문"
			);
		assertThat(MDC.get("correlationId")).isNull();
	}

	@Test
	void replacesInvalidExternalCorrelationIdWithUuid() {
		Message<?> result = interceptor.preSend(message("not-a-uuid"), channel);

		String generated = result.getHeaders().get(
			StompCorrelationInterceptor.CORRELATION_ID_HEADER,
			String.class
		);

		assertThat(UUID.fromString(generated).toString()).isEqualTo(generated);
	}

	private Message<byte[]> message(String correlationId) {
		StompHeaderAccessor accessor = StompHeaderAccessor.create(
			StompCommand.SEND
		);
		accessor.setSessionId("session-1");
		accessor.setDestination("/app/auctions/42/chat");
		accessor.addNativeHeader("X-Correlation-ID", correlationId);
		accessor.addNativeHeader(
			"Authorization",
			"Bearer header.payload.signature"
		);
		accessor.setLeaveMutable(true);
		return MessageBuilder.createMessage(
			"민감한 메시지 원문".getBytes(StandardCharsets.UTF_8),
			accessor.getMessageHeaders()
		);
	}
}
