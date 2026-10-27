package com.ssafy.bidlive.common.security;

import com.ssafy.bidlive.common.websocket.WebSocketErrorCode;

/** STOMP 세션 토큰 재인증 실패를 개인 오류 채널에 전달할 공개 오류 계약입니다. */
public enum StompTokenRenewalErrorCode implements WebSocketErrorCode {

	AUTH_RENEW_INVALID("재인증에 사용할 Access Token이 유효하지 않습니다."),
	AUTH_PRINCIPAL_MISMATCH("현재 연결의 사용자와 Access Token의 사용자가 일치하지 않습니다."),
	AUTH_RENEW_EXPIRED("재인증에 사용할 Access Token이 만료되었습니다.");

	private final String publicMessage;

	StompTokenRenewalErrorCode(String publicMessage) {
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
