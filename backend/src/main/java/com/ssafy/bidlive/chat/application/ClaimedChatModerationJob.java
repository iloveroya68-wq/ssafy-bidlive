package com.ssafy.bidlive.chat.application;

import java.util.Objects;

/** worker가 임대해 이번 시도 횟수와 함께 처리할 검열 작업입니다. */
public record ClaimedChatModerationJob(
	long jobId,
	ChatModerationJob job,
	int attemptCount
) {

	public ClaimedChatModerationJob {
		if (jobId <= 0 || attemptCount <= 0) {
			throw new IllegalArgumentException(
				"jobId and attemptCount must be positive"
			);
		}
		Objects.requireNonNull(job, "job");
	}
}
