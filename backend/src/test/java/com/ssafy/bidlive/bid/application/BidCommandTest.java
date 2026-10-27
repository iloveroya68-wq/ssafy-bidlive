package com.ssafy.bidlive.bid.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;

/** 원자 입찰 명령이 필수 식별자와 양수 금액을 보장하는지 검증한다. */
class BidCommandTest {

	private static final OffsetDateTime REQUESTED_AT = OffsetDateTime.parse("2026-07-20T21:12:44+09:00");

	@Test
	void rejectsInvalidInternalCommandValues() {
		assertThatThrownBy(() -> new BidCommand(0L, 1L, 1L, "request", REQUESTED_AT))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new BidCommand(1L, 0L, 1L, "request", REQUESTED_AT))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new BidCommand(1L, 1L, 0L, "request", REQUESTED_AT))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new BidCommand(1L, 1L, 1L, " ", REQUESTED_AT))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new BidCommand(1L, 1L, 1L, "request", null))
			.isInstanceOf(IllegalArgumentException.class);
	}
}
