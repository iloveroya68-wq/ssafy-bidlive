package com.ssafy.bidlive.prediction.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/** 리더보드 페이지의 불변 목록과 빈 스냅샷 제약을 검증한다. */
class PredictionLeaderboardPageTest {

	private static final LocalDate RANKING_DATE =
		LocalDate.parse("2026-07-22");
	private static final OffsetDateTime CALCULATED_AT =
		OffsetDateTime.parse("2026-07-23T00:05:03+09:00");

	@Test
	void copiesItemsAndAllowsEmptyPageWithoutCalculatedAt() {
		List<PredictionLeaderboardEntry> items = new ArrayList<>();
		items.add(entry());

		PredictionLeaderboardPage page = new PredictionLeaderboardPage(
			RANKING_DATE,
			CALCULATED_AT,
			items,
			null,
			false
		);
		items.clear();

		assertThat(page.items()).hasSize(1);
		assertThat(new PredictionLeaderboardPage(
			RANKING_DATE,
			null,
			List.of(),
			null,
			false
		).items()).isEmpty();
	}

	@Test
	void rejectsSnapshotDataWithoutCalculatedAt() {
		assertThatThrownBy(() -> new PredictionLeaderboardPage(
			RANKING_DATE,
			null,
			List.of(entry()),
			null,
			false
		)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new PredictionLeaderboardPage(
			RANKING_DATE,
			CALCULATED_AT,
			List.of(entry()),
			null,
			true
		)).isInstanceOf(IllegalArgumentException.class);
	}

	private PredictionLeaderboardEntry entry() {
		return new PredictionLeaderboardEntry(
			1L,
			25L,
			"member-25",
			3,
			1_500L,
			3_000L,
			new BigDecimal("200.0")
		);
	}
}
