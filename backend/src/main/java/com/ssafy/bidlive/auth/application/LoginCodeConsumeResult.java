package com.ssafy.bidlive.auth.application;

public record LoginCodeConsumeResult(Status status, Long memberId) {

	public static LoginCodeConsumeResult consumed(Long memberId) {
		return new LoginCodeConsumeResult(Status.CONSUMED, memberId);
	}

	public static LoginCodeConsumeResult alreadyUsed() {
		return new LoginCodeConsumeResult(Status.ALREADY_USED, null);
	}

	public static LoginCodeConsumeResult expired() {
		return new LoginCodeConsumeResult(Status.EXPIRED, null);
	}

	public static LoginCodeConsumeResult notFound() {
		return new LoginCodeConsumeResult(Status.NOT_FOUND, null);
	}

	public enum Status {
		CONSUMED,
		ALREADY_USED,
		EXPIRED,
		NOT_FOUND
	}
}
