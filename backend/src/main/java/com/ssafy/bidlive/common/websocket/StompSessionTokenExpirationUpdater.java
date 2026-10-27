package com.ssafy.bidlive.common.websocket;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

import com.ssafy.bidlive.common.security.StompTokenRenewalErrorCode;
import com.ssafy.bidlive.common.security.StompTokenRenewalException;
import com.ssafy.bidlive.common.security.WebSocketSessionAttributes;
import org.springframework.stereotype.Component;

/** 동시 재인증에서도 STOMP 세션에 가장 늦은 Access Token 만료 시각만 유지합니다. */
@Component
public class StompSessionTokenExpirationUpdater {

	public Instant update(
		Map<String, Object> sessionAttributes,
		Instant candidateExpiresAt
	) {
		Objects.requireNonNull(sessionAttributes, "sessionAttributes");
		Objects.requireNonNull(candidateExpiresAt, "candidateExpiresAt");

		synchronized (sessionAttributes) {
			Instant currentExpiresAt = currentExpiration(sessionAttributes);
			if (!candidateExpiresAt.isAfter(currentExpiresAt)) {
				return currentExpiresAt;
			}

			sessionAttributes.put(
				WebSocketSessionAttributes.TOKEN_EXPIRES_AT,
				candidateExpiresAt
			);
			return candidateExpiresAt;
		}
	}

	private Instant currentExpiration(Map<String, Object> sessionAttributes) {
		Object value = sessionAttributes.get(
			WebSocketSessionAttributes.TOKEN_EXPIRES_AT
		);
		if (value instanceof Instant currentExpiresAt) {
			return currentExpiresAt;
		}

		throw new StompTokenRenewalException(
			StompTokenRenewalErrorCode.AUTH_RENEW_INVALID
		);
	}
}
