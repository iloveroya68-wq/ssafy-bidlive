package com.ssafy.bidlive.chat.infrastructure.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import com.ssafy.bidlive.chat.application.ChatModerationContentHasher;
import org.springframework.stereotype.Component;

/** 검열 원문을 노출하지 않는 소문자 SHA-256 식별 해시를 생성합니다. */
@Component
public class Sha256ChatModerationContentHasher
	implements ChatModerationContentHasher {

	private static final String ALGORITHM = "SHA-256";

	@Override
	public String hash(String originalContent) {
		if (originalContent == null) {
			throw new IllegalArgumentException("originalContent must not be null");
		}
		try {
			byte[] digest = MessageDigest.getInstance(ALGORITHM)
				.digest(originalContent.getBytes(StandardCharsets.UTF_8));
			return java.util.HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException(
				"SHA-256 is not available",
				exception
			);
		}
	}
}
