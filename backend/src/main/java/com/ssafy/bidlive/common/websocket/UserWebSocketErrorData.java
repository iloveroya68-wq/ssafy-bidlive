package com.ssafy.bidlive.common.websocket;

import java.util.Objects;

/** 사용자 개인 WebSocket 오류의 공개 가능한 상세 정보를 표현합니다. */
public record UserWebSocketErrorData(
	String code,
	String message,
	String refDestination,
	String correlationId
) {

	public UserWebSocketErrorData {
		code = requireText(code, "code");
		message = requireText(message, "message");
		refDestination = validateOptionalText(refDestination, "refDestination");
		correlationId = validateOptionalText(correlationId, "correlationId");
	}

	public static UserWebSocketErrorData from(
		WebSocketErrorCode errorCode,
		String refDestination,
		String correlationId
	) {
		Objects.requireNonNull(errorCode, "errorCode");

		return new UserWebSocketErrorData(
			errorCode.code(),
			errorCode.publicMessage(),
			refDestination,
			correlationId
		);
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
