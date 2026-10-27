package com.ssafy.bidlive.chat.application;

import java.time.Instant;
import java.util.Optional;

/** 누적 위반 횟수와 이번 판정에서 적용된 뮤트 만료 시각을 제공합니다. */
public record ModerationPenaltyResult(
	long violationCount,
	Optional<Instant> mutedUntil
) {

	public ModerationPenaltyResult {
		if (violationCount <= 0) {
			throw new IllegalArgumentException("violationCount must be positive");
		}
		mutedUntil = mutedUntil == null ? Optional.empty() : mutedUntil;
	}
}
