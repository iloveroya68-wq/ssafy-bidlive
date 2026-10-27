package com.ssafy.bidlive.auth.application;

/** Refresh Token JWT 검증 실패 원인을 만료와 그 밖의 무효 상태로 구분한다. */
public class RefreshTokenVerificationException extends RuntimeException {

	private final Reason reason;

	public RefreshTokenVerificationException(Reason reason, Throwable cause) {
		super(reason.name(), cause);
		this.reason = reason;
	}

	public Reason getReason() {
		return reason;
	}

	public enum Reason {
		INVALID,
		EXPIRED
	}
}
