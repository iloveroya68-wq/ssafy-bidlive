package com.ssafy.bidlive.chat.application;

import java.util.Objects;

/** 채팅 메시지 삭제 실패를 안정적인 공개 오류 코드로 전달한다. */
public class ChatMessageDeletionException extends RuntimeException {

	private final ChatMessageDeletionErrorCode errorCode;

	public ChatMessageDeletionException(
		ChatMessageDeletionErrorCode errorCode
	) {
		this(errorCode, null);
	}

	public ChatMessageDeletionException(
		ChatMessageDeletionErrorCode errorCode,
		Throwable cause
	) {
		super(Objects.requireNonNull(errorCode, "errorCode").name(), cause);
		this.errorCode = errorCode;
	}

	public ChatMessageDeletionErrorCode errorCode() {
		return errorCode;
	}
}
