package com.ssafy.bidlive.chat.application;

/** 슬로우 모드 허용 여부와 제한 해제까지 남은 초를 전달한다. */
public record ChatSlowModeResult(
	boolean allowed,
	long retryAfterSeconds
) {

	public ChatSlowModeResult {
		if (allowed && retryAfterSeconds != 0) {
			throw new IllegalArgumentException(
				"allowed result must have zero retryAfterSeconds"
			);
		}
		if (!allowed && retryAfterSeconds <= 0) {
			throw new IllegalArgumentException(
				"limited result must have positive retryAfterSeconds"
			);
		}
	}

	public static ChatSlowModeResult permit() {
		return new ChatSlowModeResult(true, 0);
	}

	public static ChatSlowModeResult limited(long retryAfterSeconds) {
		return new ChatSlowModeResult(false, retryAfterSeconds);
	}
}
