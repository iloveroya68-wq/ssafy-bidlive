package com.ssafy.bidlive.common.websocket;

import java.util.Objects;

/** 사용자 개인 오류를 공통 type과 data 형식으로 감싼 WebSocket 메시지입니다. */
public record UserWebSocketErrorMessage(
	String type,
	UserWebSocketErrorData data
) {

	private static final String ERROR_TYPE = "ERROR";

	public UserWebSocketErrorMessage {
		Objects.requireNonNull(type, "type");
		Objects.requireNonNull(data, "data");

		if (!ERROR_TYPE.equals(type)) {
			throw new IllegalArgumentException(
				"type은 ERROR여야 합니다."
			);
		}
	}

	public static UserWebSocketErrorMessage error(
		WebSocketErrorCode errorCode,
		String refDestination,
		String correlationId
	) {
		return new UserWebSocketErrorMessage(
			ERROR_TYPE,
			UserWebSocketErrorData.from(
				errorCode,
				refDestination,
				correlationId
			)
		);
	}
}
