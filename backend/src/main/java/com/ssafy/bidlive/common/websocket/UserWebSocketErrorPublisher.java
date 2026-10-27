package com.ssafy.bidlive.common.websocket;

/** 현재 STOMP 요청 세션의 개인 오류 큐로 안전한 오류 메시지를 발행합니다. */
public interface UserWebSocketErrorPublisher {

	void publish(
		StompErrorContext context,
		WebSocketErrorCode errorCode
	);
}
