package com.ssafy.bidlive.chat.application;

import java.util.Objects;

/** LLM 실패를 원문이나 응답 본문 없이 재시도 가능 여부와 코드로 전달합니다. */
public class ChatLlmModerationException extends RuntimeException {

	private final ChatLlmFailureCode failureCode;
	private final boolean retryable;

	public ChatLlmModerationException(
		ChatLlmFailureCode failureCode,
		boolean retryable,
		Throwable cause
	) {
		super(Objects.requireNonNull(failureCode, "failureCode").name(), cause);
		this.failureCode = failureCode;
		this.retryable = retryable;
	}

	public ChatLlmFailureCode failureCode() {
		return failureCode;
	}

	public boolean retryable() {
		return retryable;
	}
}
