package com.ssafy.bidlive.common.websocket.send;

import com.ssafy.bidlive.common.websocket.WebSocketErrorCode;

/** STOMP 전송 거부 시 사용자에게 공개할 오류 코드와 메시지를 제공합니다. */
public enum StompSendErrorCode implements WebSocketErrorCode {

	SEND_DENIED("해당 destination으로 메시지를 보낼 수 없습니다.");

	private final String publicMessage;

	StompSendErrorCode(String publicMessage) {
		this.publicMessage = publicMessage;
	}

	@Override
	public String code() {
		return name();
	}

	@Override
	public String publicMessage() {
		return publicMessage;
	}
}
