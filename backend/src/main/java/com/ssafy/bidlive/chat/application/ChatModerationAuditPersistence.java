package com.ssafy.bidlive.chat.application;

import java.time.Instant;

/** 암호화된 검열 감사 레코드의 멱등 저장과 만료 정리를 정의합니다. */
public interface ChatModerationAuditPersistence {

	void saveIfAbsent(ChatModerationAuditEntry entry);

	int deleteExpired(Instant now);
}
