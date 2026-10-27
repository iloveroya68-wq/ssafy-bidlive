package com.ssafy.bidlive.chat.application;

/** 검열 원문을 애플리케이션 경계에서 암복호화하는 계약입니다. */
public interface ChatModerationAuditCipher {

	EncryptedChatContent encrypt(
		ChatModerationAuditContext context,
		String originalContent
	);

	String decrypt(
		ChatModerationAuditContext context,
		EncryptedChatContent encryptedContent
	);
}
