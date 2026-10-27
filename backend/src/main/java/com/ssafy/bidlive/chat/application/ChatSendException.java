package com.ssafy.bidlive.chat.application;

import java.util.Objects;

/** 채팅 전송 정책 실패를 안정적인 공개 오류 코드와 함께 전달한다. */
public class ChatSendException extends RuntimeException {

	private final ChatSendErrorCode errorCode;

	public ChatSendException(ChatSendErrorCode errorCode) {
		this(errorCode, null);
	}

	public ChatSendException(
		ChatSendErrorCode errorCode,
		Throwable cause
	) {
		super(Objects.requireNonNull(errorCode, "errorCode").code(), cause);
		this.errorCode = errorCode;
	}

	public ChatSendErrorCode errorCode() {
		return errorCode;
	}
}
