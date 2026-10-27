package com.ssafy.bidlive.chat.application;

/** 검열 원문을 평문 없이 저장하기 위한 AES-GCM 암호문과 메타데이터입니다. */
public record EncryptedChatContent(
	String ciphertext,
	String iv,
	String keyVersion
) {

	public EncryptedChatContent {
		ciphertext = requireText(ciphertext, "ciphertext");
		iv = requireText(iv, "iv");
		keyVersion = requireText(keyVersion, "keyVersion");
	}

	private static String requireText(String value, String name) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(name + " must not be blank");
		}
		return value.strip();
	}
}
