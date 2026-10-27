package com.ssafy.bidlive.auction.application;

import java.time.Instant;

/** 판매자 음성 청크에서 확정된 순서 있는 STT 문장이다. */
public record CatchUpTranscriptSegment(
	long sequence,
	String text,
	Instant startedAt,
	Instant endedAt
) {
	public CatchUpTranscriptSegment {
		if (sequence < 0) {
			throw new IllegalArgumentException("sequence must not be negative");
		}
		if (text == null || text.isBlank()) {
			throw new IllegalArgumentException("text must not be blank");
		}
	}
}
