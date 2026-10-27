package com.ssafy.bidlive.chat.application;

/** 비동기 검열 재시도와 DLQ 기록에 사용할 내부 실패 코드를 정의합니다. */
public enum ChatLlmFailureCode {
	LLM_TIMEOUT,
	LLM_RESPONSE_INVALID,
	LLM_TRANSPORT_ERROR,
	LLM_RETRY_EXHAUSTED
}
