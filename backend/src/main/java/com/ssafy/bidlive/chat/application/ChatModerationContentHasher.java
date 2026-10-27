package com.ssafy.bidlive.chat.application;

/** 평문 대신 분석·추적에 사용할 단방향 채팅 원문 해시를 생성합니다. */
public interface ChatModerationContentHasher {

	String hash(String originalContent);
}
