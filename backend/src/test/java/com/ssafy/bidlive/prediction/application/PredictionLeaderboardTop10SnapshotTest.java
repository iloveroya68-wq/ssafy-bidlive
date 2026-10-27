package com.ssafy.bidlive.prediction.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.LongStream;

import org.junit.jupiter.api.Test;

/** 상위 10명 캐시 값의 크기·커서·불변성 제약을 검증한다. */
class PredictionLeaderboardTop10SnapshotTest {

	private static final LocalDate RANKING_DATE =
		LocalDate.parse("2026-07-22");
	private static final OffsetDateTime CALCULATED_AT =
		OffsetDateTime.parse("2026-07-23T00:05:03+09:00");

	@Test
	void copiesItemsAndAcceptsAtMostTenEntries() {
		List<PredictionLeaderboardEntry> items = new ArrayList<>();
		items.add(entry(1L));
		PredictionLeaderboardTop10Snapshot snapshot =
			new PredictionLeaderboardTop10Snapshot(
				RANKING_DATE,
				CALCULATED_AT,
				items,
				null,
				false
			);

		items.clear();

		assertThat(snapshot.items()).hasSize(1);
		assertThatThrownBy(() -> snapshot.items().clear())
			.isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void rejectsMoreThanTenEntries() {
		List<PredictionLeaderboardEntry> items = LongStream.rangeClosed(1, 11)
			.mapToObj(PredictionLeaderboardTop10SnapshotTest::entry)
			.toList();

		assertThatThrownBy(() -> new PredictionLeaderboardTop10Snapshot(
			RANKING_DATE,
			CALCULATED_AT,
			items,
			"cursor",
			true
		)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("more than 10");
	}

	@Test
	void requiresCursorOnlyWhenAnotherPageExists() {
		assertThatThrownBy(() -> new PredictionLeaderboardTop10Snapshot(
			RANKING_DATE,
			CALCULATED_AT,
			List.of(entry(1L)),
			null,
			true
		)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new PredictionLeaderboardTop10Snapshot(
			RANKING_DATE,
			CALCULATED_AT,
			List.of(entry(1L)),
			"cursor",
			false
		)).isInstanceOf(IllegalArgumentException.class);
	}

	private static PredictionLeaderboardEntry entry(long rank) {
		return new PredictionLeaderboardEntry(
			rank,
			rank + 100,
			"member-" + rank,
			1,
			100L,
			200L,
			new BigDecimal("200.0")
		);
	}
}
