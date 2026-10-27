package com.ssafy.bidlive.chat.application;

/** 채팅 메시지 삭제 API가 노출하는 안정적인 실패 코드를 정의한다. */
public enum ChatMessageDeletionErrorCode {
	INVALID_AUCTION_ID("경매 ID가 올바르지 않습니다."),
	INVALID_MESSAGE_ID("메시지 ID가 올바르지 않습니다."),
	UNAUTHORIZED("인증이 필요합니다."),
	CHAT_DELETE_FORBIDDEN("채팅 메시지를 삭제할 권한이 없습니다."),
	MESSAGE_NOT_FOUND("메시지를 찾을 수 없습니다.");

	private final String publicMessage;

	ChatMessageDeletionErrorCode(String publicMessage) {
		this.publicMessage = publicMessage;
	}

	public String publicMessage() {
		return publicMessage;
	}
}
