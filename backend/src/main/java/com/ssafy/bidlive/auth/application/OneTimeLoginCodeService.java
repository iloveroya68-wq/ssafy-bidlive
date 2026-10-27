package com.ssafy.bidlive.auth.application;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

import org.springframework.stereotype.Service;

@Service
public class OneTimeLoginCodeService {

	private static final int LOGIN_CODE_BYTES = 32;

	private final SecureRandom secureRandom = new SecureRandom();
	private final OneTimeLoginCodeStore loginCodeStore;

	public OneTimeLoginCodeService(OneTimeLoginCodeStore loginCodeStore) {
		this.loginCodeStore = loginCodeStore;
	}

	public String issue(Long memberId, Duration ttl) {
		byte[] randomBytes = new byte[LOGIN_CODE_BYTES];
		secureRandom.nextBytes(randomBytes);
		String loginCode = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
		loginCodeStore.save(loginCode, memberId, ttl);
		return loginCode;
	}

	public LoginCodeConsumeResult consume(String loginCode) {
		return loginCodeStore.consume(loginCode);
	}
}
