package com.ssafy.bidlive.report.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;

/**
 * 커서가 깨지면 페이지가 겹치거나 빠지는데 화면은 멀쩡해 보인다 — 왕복을 잠근다.
 */
class ReportedAuctionCursorTest {

	private static final Instant AT = Instant.parse("2026-07-31T10:31:00Z");

	@Test
	void roundTripsThroughEncoding() {
		ReportedAuctionCursor decoded =
			ReportedAuctionCursor.decode(new ReportedAuctionCursor(AT, 9203L).encode());

		assertThat(decoded.latestReportedAt()).isEqualTo(AT);
		assertThat(decoded.auctionId()).isEqualTo(9203L);
	}

	@Test
	void encodesToUrlSafeTextWithoutPadding() {
		String encoded = new ReportedAuctionCursor(AT, 9203L).encode();

		assertThat(encoded).doesNotContain("=", "+", "/");
	}

	@Test
	void rejectsMalformedCursor() {
		assertThatThrownBy(() -> ReportedAuctionCursor.decode("not-a-cursor"))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsNonPositiveAuctionId() {
		assertThatThrownBy(() -> new ReportedAuctionCursor(AT, 0L))
			.isInstanceOf(IllegalArgumentException.class);
	}
}
