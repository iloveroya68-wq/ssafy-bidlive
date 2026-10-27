package com.ssafy.bidlive.common.websocket.subscription;

import com.ssafy.bidlive.common.websocket.WebSocketErrorCode;

/** STOMP 구독 거부 시 사용자에게 공개할 오류 코드와 메시지를 제공합니다. */
public enum StompSubscriptionErrorCode implements WebSocketErrorCode {

	SUBSCRIPTION_DENIED("해당 채널을 구독할 권한이 없습니다.");

	private final String publicMessage;

	StompSubscriptionErrorCode(String publicMessage) {
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
