package com.ssafy.bidlive.common.security;

import com.ssafy.bidlive.common.websocket.WebSocketErrorCode;

/** STOMP 인증 실패를 클라이언트에 안정적으로 전달하기 위한 공개 오류 코드입니다. */
public enum StompAuthenticationErrorCode implements WebSocketErrorCode {

	AUTH_INVALID("인증 정보가 유효하지 않습니다."),
	AUTH_EXPIRED("인증 토큰이 만료되었습니다.");

	private final String publicMessage;

	StompAuthenticationErrorCode(String publicMessage) {
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
