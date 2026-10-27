package com.ssafy.bidlive.common.websocket;

import java.util.Objects;

import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/** SEND·SUBSCRIBE frame에서 개인 오류 전송에 필요한 서버 검증 문맥을 추출합니다. */
@Component
public class StompErrorContextResolver {

	public StompErrorContext resolve(Message<?> message) {
		return resolve(message, null);
	}

	public StompErrorContext resolve(
		Message<?> message,
		String explicitCorrelationId
	) {
		Objects.requireNonNull(message, "message");

		StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(
			message,
			StompHeaderAccessor.class
		);
		if (accessor == null) {
			throw new IllegalArgumentException(
				"STOMP 메시지 접근자를 찾을 수 없습니다."
			);
		}

		validateCommand(accessor.getCommand());

		return new StompErrorContext(
			accessor.getUser(),
			accessor.getSessionId(),
			accessor.getDestination(),
			resolveCorrelationId(explicitCorrelationId, accessor.getReceipt())
		);
	}

	private void validateCommand(StompCommand command) {
		if (command != StompCommand.SEND && command != StompCommand.SUBSCRIBE) {
			throw new IllegalArgumentException(
				"SEND와 SUBSCRIBE frame만 지원합니다."
			);
		}
	}

	private String resolveCorrelationId(
		String explicitCorrelationId,
		String receiptId
	) {
		if (hasText(explicitCorrelationId)) {
			return explicitCorrelationId;
		}
		if (hasText(receiptId)) {
			return receiptId;
		}
		return null;
	}

	private boolean hasText(String value) {
		return value != null && !value.isBlank();
	}
}
