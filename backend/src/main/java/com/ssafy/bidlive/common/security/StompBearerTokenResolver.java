package com.ssafy.bidlive.common.security;

import java.util.List;

import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;

/** STOMP CONNECT의 단일 Authorization native header에서 Bearer Token을 추출합니다. */
@Component
public class StompBearerTokenResolver {

	private static final String AUTHORIZATION_HEADER = "Authorization";
	private static final String BEARER_PREFIX = "Bearer ";

	public String resolve(StompHeaderAccessor accessor) {
		if (accessor == null) {
			throw invalidAuthentication();
		}

		List<String> authorizationHeaders = accessor.getNativeHeader(AUTHORIZATION_HEADER);
		if (authorizationHeaders == null || authorizationHeaders.size() != 1) {
			throw invalidAuthentication();
		}

		String authorization = authorizationHeaders.getFirst();
		if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
			throw invalidAuthentication();
		}

		String token = authorization.substring(BEARER_PREFIX.length());
		if (token.isBlank() || token.chars().anyMatch(Character::isWhitespace)) {
			throw invalidAuthentication();
		}
		return token;
	}

	private StompAuthenticationException invalidAuthentication() {
		return new StompAuthenticationException(StompAuthenticationErrorCode.AUTH_INVALID);
	}
}
