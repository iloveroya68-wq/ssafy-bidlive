package com.ssafy.bidlive.chat.application;

import java.util.Objects;

/** 채팅 이력 조회 정책 위반과 입력 오류를 안정적인 오류 코드로 전달한다. */
public class ChatHistoryException extends RuntimeException {

	private final ChatHistoryErrorCode errorCode;

	public ChatHistoryException(ChatHistoryErrorCode errorCode) {
		this(errorCode, null);
	}

	public ChatHistoryException(
		ChatHistoryErrorCode errorCode,
		Throwable cause
	) {
		super(Objects.requireNonNull(errorCode, "errorCode").name(), cause);
		this.errorCode = errorCode;
	}

	public ChatHistoryErrorCode errorCode() {
		return errorCode;
	}
}
