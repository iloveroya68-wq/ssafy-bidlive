package com.ssafy.bidlive.common.websocket;

/** 사용자에게 공개할 수 있는 WebSocket 오류 코드와 고정 메시지를 제공합니다. */
public interface WebSocketErrorCode {

	String code();

	String publicMessage();
}
