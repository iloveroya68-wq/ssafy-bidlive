package com.ssafy.bidlive.chat.application;

import java.util.Objects;

import com.ssafy.bidlive.chat.domain.QuestionQueueErrorCode;

/** 질문 큐 유스케이스 실패를 안정적인 오류 코드로 전달한다. */
public class QuestionQueueException extends RuntimeException {

	private final QuestionQueueErrorCode errorCode;

	public QuestionQueueException(QuestionQueueErrorCode errorCode) {
		super(errorCode.name());
		this.errorCode = Objects.requireNonNull(errorCode, "errorCode");
	}

	public QuestionQueueException(
		QuestionQueueErrorCode errorCode,
		Throwable cause
	) {
		super(errorCode.name(), cause);
		this.errorCode = Objects.requireNonNull(errorCode, "errorCode");
	}

	public QuestionQueueErrorCode errorCode() {
		return errorCode;
	}
}
