package com.ssafy.bidlive.common.api.dto;

/** STOMP 세션 재인증에 사용할 새 Access Token 요청을 표현합니다. */
public record StompTokenRenewalRequest(
	String accessToken
) {

	@Override
	public String toString() {
		return "StompTokenRenewalRequest[accessToken=[REDACTED]]";
	}
}
