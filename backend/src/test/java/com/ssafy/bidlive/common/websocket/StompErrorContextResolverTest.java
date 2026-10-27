package com.ssafy.bidlive.common.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.Principal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

/** STOMP 오류 문맥이 서버 Principal과 요청 frame 정보로만 구성되는지 검증합니다. */
class StompErrorContextResolverTest {

	private static final Principal PRINCIPAL = new TestPrincipal("25");
	private static final String SESSION_ID = "session-123";
	private static final String DESTINATION = "/topic/auctions/100/questions";
	private static final String RECEIPT_ID = "receipt-123";
	private static final String EXPLICIT_CORRELATION_ID = "client-message-123";

	private final StompErrorContextResolver resolver = new StompErrorContextResolver();

	@Test
	void resolvesSendContextWithExplicitCorrelationId() {
		Message<byte[]> message = message(
			StompCommand.SEND,
			PRINCIPAL,
			SESSION_ID,
			DESTINATION,
			RECEIPT_ID,
			"memberId=999"
		);

		StompErrorContext result = resolver.resolve(
			message,
			EXPLICIT_CORRELATION_ID
		);

		assertThat(result.principal()).isSameAs(PRINCIPAL);
		assertThat(result.sessionId()).isEqualTo(SESSION_ID);
		assertThat(result.refDestination()).isEqualTo(DESTINATION);
		assertThat(result.correlationId()).isEqualTo(EXPLICIT_CORRELATION_ID);
	}

	@Test
	void resolvesSubscribeContextWithReceiptId() {
		Message<byte[]> message = message(
			StompCommand.SUBSCRIBE,
			PRINCIPAL,
			SESSION_ID,
			DESTINATION,
			RECEIPT_ID,
			""
		);

		StompErrorContext result = resolver.resolve(message);

		assertThat(result.principal()).isSameAs(PRINCIPAL);
		assertThat(result.sessionId()).isEqualTo(SESSION_ID);
		assertThat(result.refDestination()).isEqualTo(DESTINATION);
		assertThat(result.correlationId()).isEqualTo(RECEIPT_ID);
	}

	@Test
	void allowsMissingCorrelationId() {
		Message<byte[]> message = message(
			StompCommand.SEND,
			PRINCIPAL,
			SESSION_ID,
			DESTINATION,
			null,
			""
		);

		StompErrorContext result = resolver.resolve(message);

		assertThat(result.correlationId()).isNull();
	}

	@Test
	void fallsBackToReceiptWhenExplicitCorrelationIdIsBlank() {
		Message<byte[]> message = message(
			StompCommand.SEND,
			PRINCIPAL,
			SESSION_ID,
			DESTINATION,
			RECEIPT_ID,
			""
		);

		StompErrorContext result = resolver.resolve(message, " ");

		assertThat(result.correlationId()).isEqualTo(RECEIPT_ID);
	}

	@Test
	void ignoresClientMemberIdWhenResolvingPrincipal() {
		StompHeaderAccessor accessor = accessor(
			StompCommand.SEND,
			PRINCIPAL,
			SESSION_ID,
			DESTINATION,
			RECEIPT_ID
		);
		accessor.addNativeHeader("memberId", "999");
		Message<byte[]> message = buildMessage(accessor, "memberId=999");

		StompErrorContext result = resolver.resolve(message);

		assertThat(result.principal()).isSameAs(PRINCIPAL);
		assertThat(result.principal().getName()).isEqualTo("25");
	}

	@ParameterizedTest
	@EnumSource(value = StompCommand.class, names = {"CONNECT", "DISCONNECT"})
	void rejectsUnsupportedFrame(StompCommand command) {
		Message<byte[]> message = message(
			command,
			PRINCIPAL,
			SESSION_ID,
			DESTINATION,
			RECEIPT_ID,
			""
		);

		assertThatThrownBy(() -> resolver.resolve(message))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("SEND와 SUBSCRIBE frame만 지원합니다.");
	}

	@Test
	void rejectsMessageWithoutStompAccessor() {
		Message<String> message = MessageBuilder.withPayload("payload").build();

		assertThatThrownBy(() -> resolver.resolve(message))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("STOMP 메시지 접근자를 찾을 수 없습니다.");
	}

	@Test
	void rejectsMissingPrincipal() {
		Message<byte[]> message = message(
			StompCommand.SEND,
			null,
			SESSION_ID,
			DESTINATION,
			RECEIPT_ID,
			""
		);

		assertThatThrownBy(() -> resolver.resolve(message))
			.isInstanceOf(NullPointerException.class)
			.hasMessage("principal");
	}

	@Test
	void rejectsMissingSessionId() {
		Message<byte[]> message = message(
			StompCommand.SEND,
			PRINCIPAL,
			null,
			DESTINATION,
			RECEIPT_ID,
			""
		);

		assertThatThrownBy(() -> resolver.resolve(message))
			.isInstanceOf(NullPointerException.class)
			.hasMessage("sessionId");
	}

	@Test
	void rejectsMissingDestination() {
		Message<byte[]> message = message(
			StompCommand.SEND,
			PRINCIPAL,
			SESSION_ID,
			null,
			RECEIPT_ID,
			""
		);

		assertThatThrownBy(() -> resolver.resolve(message))
			.isInstanceOf(NullPointerException.class)
			.hasMessage("refDestination");
	}

	private Message<byte[]> message(
		StompCommand command,
		Principal principal,
		String sessionId,
		String destination,
		String receiptId,
		String body
	) {
		return buildMessage(
			accessor(command, principal, sessionId, destination, receiptId),
			body
		);
	}

	private StompHeaderAccessor accessor(
		StompCommand command,
		Principal principal,
		String sessionId,
		String destination,
		String receiptId
	) {
		StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
		accessor.setUser(principal);
		accessor.setSessionId(sessionId);
		accessor.setDestination(destination);
		accessor.setReceipt(receiptId);
		accessor.setLeaveMutable(true);
		return accessor;
	}

	private Message<byte[]> buildMessage(
		StompHeaderAccessor accessor,
		String body
	) {
		return MessageBuilder.createMessage(
			body.getBytes(StandardCharsets.UTF_8),
			accessor.getMessageHeaders()
		);
	}

	private record TestPrincipal(String name) implements Principal {

		@Override
		public String getName() {
			return name;
		}
	}
}
