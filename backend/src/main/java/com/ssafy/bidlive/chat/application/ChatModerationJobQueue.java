package com.ssafy.bidlive.chat.application;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/** messageId 멱등 키로 비동기 검열 작업을 내구성 큐에 등록합니다. */
public interface ChatModerationJobQueue {

	boolean enqueue(ChatModerationJob job);

	Optional<ClaimedChatModerationJob> claimDue(
		Instant now,
		Duration lease,
		int maxAttempts
	);

	void succeed(long jobId, Instant now);

	void retry(
		long jobId,
		Instant nextAttemptAt,
		ChatLlmFailureCode failureCode,
		Instant now
	);

	void deadLetter(
		long jobId,
		ChatLlmFailureCode failureCode,
		Instant now
	);

	int recoverExhaustedLeases(
		Instant staleBefore,
		int maxAttempts,
		Instant now
	);
}
