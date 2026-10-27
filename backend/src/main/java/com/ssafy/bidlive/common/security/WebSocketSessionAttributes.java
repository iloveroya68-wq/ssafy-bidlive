package com.ssafy.bidlive.common.security;

/** 인증된 WebSocket 세션에서 공유하는 속성 이름을 정의합니다. */
public final class WebSocketSessionAttributes {

	public static final String TOKEN_EXPIRES_AT = "tokenExpiresAt";
	public static final String CONNECTED_AT = "connectedAt";

	private WebSocketSessionAttributes() {
	}
}
