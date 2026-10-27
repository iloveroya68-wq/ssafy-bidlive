package com.ssafy.bidlive.common.websocket;

import java.security.Principal;
import java.util.Objects;

/** 개인 오류를 원래 STOMP 요청 세션과 연결하는 안전한 요청 문맥을 표현합니다. */
public record StompErrorContext(
	Principal principal,
	String sessionId,
	String refDestination,
	String correlationId
) {

	public StompErrorContext {
		Objects.requireNonNull(principal, "principal");
		sessionId = requireText(sessionId, "sessionId");
		refDestination = requireText(refDestination, "refDestination");
		correlationId = validateOptionalText(correlationId, "correlationId");
	}

	private static String requireText(String value, String fieldName) {
		Objects.requireNonNull(value, fieldName);

		if (value.isBlank()) {
			throw new IllegalArgumentException(
				fieldName + "은 비어 있을 수 없습니다."
			);
		}

		return value;
	}

	private static String validateOptionalText(String value, String fieldName) {
		if (value != null && value.isBlank()) {
			throw new IllegalArgumentException(
				fieldName + "은 빈 문자열일 수 없습니다."
			);
		}

		return value;
	}
}
