package com.ssafy.bidlive.chat.application;

import com.ssafy.bidlive.common.websocket.WebSocketErrorCode;

/** 채팅 전송 실패를 공개 가능한 코드와 고정 문구로 정의한다. */
public enum ChatSendErrorCode implements WebSocketErrorCode {

	AUCTION_CHAT_CLOSED("현재 경매에서는 채팅을 보낼 수 없습니다."),
	CHAT_MUTED("일시적으로 채팅이 제한되었습니다."),
	SLOW_MODE_LIMIT("잠시 후 다시 채팅을 보내 주세요."),
	CHAT_CONTENT_INVALID("채팅 내용을 확인해 주세요.");

	private final String publicMessage;

	ChatSendErrorCode(String publicMessage) {
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
