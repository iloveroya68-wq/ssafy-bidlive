package com.ssafy.bidlive.media.api;

import org.springframework.security.core.Authentication;

/** 라이브 API 진입점들이 공유하는 인증 회원 식별자 추출 규칙입니다. */
final class LiveRequestAuthentication {

	private LiveRequestAuthentication() {
	}

	static Long memberId(Authentication authentication) {
		if (authentication == null || !authentication.isAuthenticated()) {
			throw new LiveUnauthenticatedException();
		}
		try {
			Long memberId = Long.valueOf(authentication.getName());
			if (memberId <= 0) {
				throw new NumberFormatException("member ID must be positive");
			}
			return memberId;
		} catch (NumberFormatException exception) {
			throw new LiveUnauthenticatedException(exception);
		}
	}
}
