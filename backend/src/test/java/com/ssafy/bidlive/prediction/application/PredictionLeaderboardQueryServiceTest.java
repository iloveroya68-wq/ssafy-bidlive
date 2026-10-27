package com.ssafy.bidlive.prediction.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import com.ssafy.bidlive.prediction.domain.PredictionLeaderboardQueryErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 일일 리더보드의 기본 날짜·캐시 분기·커서 페이지·빈 결과를 검증한다. */
@ExtendWith(MockitoExtension.class)
class PredictionLeaderboardQueryServiceTest {

	private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
	private static final LocalDate RANKING_DATE =
		LocalDate.parse("2026-07-22");
	private static final OffsetDateTime CALCULATED_AT =
		OffsetDateTime.parse("2026-07-23T00:05:03+09:00");
	private static final Clock CLOCK = Clock.fixed(
		Instant.parse("2026-07-23T03:00:00Z"),
		SEOUL_ZONE
	);

	@Mock
	private PredictionLeaderboardTop10Cache cache;

	@Mock
	private PredictionLeaderboardQueryPersistence persistence;

	private PredictionLeaderboardQueryService service;

	@BeforeEach
	void setUp() {
		service = new PredictionLeaderboardQueryService(
			cache,
			persistence,
			CLOCK
		);
	}

	@Test
	void defaultsToPreviousSeoulDayAndUsesCachedTop10() {
		PredictionLeaderboardTop10Snapshot top10 = top10();
		PredictionLeaderboardEntry myRank = entry(18L, 25L);
		when(cache.find(RANKING_DATE)).thenReturn(Optional.of(top10));
		when(persistence.findMyRank(RANKING_DATE, CALCULATED_AT, 25L))
			.thenReturn(Optional.of(myRank));

		PredictionLeaderboardQueryResult result = service.query(
			null,
			10,
			null,
			25L
		);

		assertThat(result.rankingDate()).isEqualTo(RANKING_DATE);
		assertThat(result.items()).isEqualTo(top10.items());
		assertThat(result.myRank()).isEqualTo(myRank);
		verify(persistence, never()).findPage(RANKING_DATE, null, 10);
	}

	@Test
	void reloadsAndCachesDefaultPageOnCacheMiss() {
		PredictionLeaderboardPage page = page(
			List.of(entry(1L, 101L)),
			"next-cursor",
			true
		);
		when(cache.find(RANKING_DATE)).thenReturn(Optional.empty());
		when(persistence.findPage(RANKING_DATE, null, 10))
			.thenReturn(page);
		when(persistence.findMyRank(RANKING_DATE, CALCULATED_AT, 25L))
			.thenReturn(Optional.empty());

		PredictionLeaderboardQueryResult result = service.query(
			RANKING_DATE,
			10,
			null,
			25L
		);

		assertThat(result.items()).isEqualTo(page.items());
		assertThat(result.myRank()).isNull();
		verify(cache).save(new PredictionLeaderboardTop10Snapshot(
			RANKING_DATE,
			CALCULATED_AT,
			page.items(),
			page.nextCursor(),
			page.hasNext()
		));
	}

	@Test
	void bypassesTop10CacheForCustomSizeAndDecodedCursor() {
		PredictionLeaderboardCursor cursor =
			new PredictionLeaderboardCursor(
				RANKING_DATE,
				CALCULATED_AT,
				10L,
				110L
			);
		PredictionLeaderboardPage page = page(
			List.of(entry(11L, 111L)),
			null,
			false
		);
		when(persistence.findPage(RANKING_DATE, cursor, 20))
			.thenReturn(page);
		when(persistence.findMyRank(RANKING_DATE, CALCULATED_AT, 25L))
			.thenReturn(Optional.empty());

		PredictionLeaderboardQueryResult result = service.query(
			RANKING_DATE,
			20,
			cursor.encode(),
			25L
		);

		assertThat(result.items()).extracting(
			PredictionLeaderboardEntry::rank
		).containsExactly(11L);
		verify(cache, never()).find(RANKING_DATE);
	}

	@Test
	void returnsEmptyResultWithoutMemberQueryOrCacheWrite() {
		PredictionLeaderboardPage empty = new PredictionLeaderboardPage(
			RANKING_DATE,
			null,
			List.of(),
			null,
			false
		);
		when(cache.find(RANKING_DATE)).thenReturn(Optional.empty());
		when(persistence.findPage(RANKING_DATE, null, 10))
			.thenReturn(empty);

		PredictionLeaderboardQueryResult result = service.query(
			RANKING_DATE,
			10,
			null,
			25L
		);

		assertThat(result.calculatedAt()).isNull();
		assertThat(result.items()).isEmpty();
		assertThat(result.myRank()).isNull();
		assertThat(result.nextCursor()).isNull();
		assertThat(result.hasNext()).isFalse();
		verify(cache, never()).save(
			org.mockito.ArgumentMatchers.any()
		);
		verify(persistence, never()).findMyRank(
			org.mockito.ArgumentMatchers.any(),
			org.mockito.ArgumentMatchers.any(),
			org.mockito.ArgumentMatchers.anyLong()
		);
	}

	@Test
	void rejectsInvalidDateSizeMemberAndCursor() {
		PredictionLeaderboardCursor otherDateCursor =
			new PredictionLeaderboardCursor(
				RANKING_DATE.minusDays(1),
				CALCULATED_AT,
				10L,
				110L
			);

		assertQueryError(
			() -> service.query(
				LocalDate.parse("2026-07-23"),
				10,
				null,
				25L
			),
			PredictionLeaderboardQueryErrorCode.INVALID_RANKING_DATE
		);
		assertQueryError(
			() -> service.query(RANKING_DATE, 0, null, 25L),
			PredictionLeaderboardQueryErrorCode.INVALID_PAGE_SIZE
		);
		assertQueryError(
			() -> service.query(RANKING_DATE, 101, null, 25L),
			PredictionLeaderboardQueryErrorCode.INVALID_PAGE_SIZE
		);
		assertQueryError(
			() -> service.query(RANKING_DATE, 10, null, 0L),
			PredictionLeaderboardQueryErrorCode.UNAUTHORIZED
		);
		assertQueryError(
			() -> service.query(
				RANKING_DATE,
				20,
				"not*base64*",
				25L
			),
			PredictionLeaderboardQueryErrorCode.INVALID_CURSOR
		);
		assertQueryError(
			() -> service.query(
				RANKING_DATE,
				20,
				otherDateCursor.encode(),
				25L
			),
			PredictionLeaderboardQueryErrorCode.INVALID_CURSOR
		);
	}

	@Test
	void rejectsCursorFromReplacedSnapshotWhenPageExposesNewVersion() {
		PredictionLeaderboardCursor cursor =
			new PredictionLeaderboardCursor(
				RANKING_DATE,
				CALCULATED_AT,
				10L,
				110L
			);
		PredictionLeaderboardPage replaced = new PredictionLeaderboardPage(
			RANKING_DATE,
			CALCULATED_AT.plusMinutes(1),
			List.of(entry(11L, 111L)),
			null,
			false
		);
		when(persistence.findPage(RANKING_DATE, cursor, 20))
			.thenReturn(replaced);

		assertQueryError(
			() -> service.query(
				RANKING_DATE,
				20,
				cursor.encode(),
				25L
			),
			PredictionLeaderboardQueryErrorCode.INVALID_CURSOR
		);
	}

	@Test
	void refreshesTop10WithoutQueryingMemberRank() {
		PredictionLeaderboardTop10Snapshot top10 = top10();
		when(persistence.findTop10(RANKING_DATE, CALCULATED_AT))
			.thenReturn(top10);

		assertThat(service.refreshTop10(RANKING_DATE, CALCULATED_AT))
			.isSameAs(top10);
		verify(cache).save(top10);
		verify(persistence, never()).findMyRank(
			RANKING_DATE,
			CALCULATED_AT,
			25L
		);
	}

	private PredictionLeaderboardTop10Snapshot top10() {
		return new PredictionLeaderboardTop10Snapshot(
			RANKING_DATE,
			CALCULATED_AT,
			List.of(entry(1L, 101L)),
			"next-cursor",
			true
		);
	}

	private PredictionLeaderboardPage page(
		List<PredictionLeaderboardEntry> items,
		String nextCursor,
		boolean hasNext
	) {
		return new PredictionLeaderboardPage(
			RANKING_DATE,
			CALCULATED_AT,
			items,
			nextCursor,
			hasNext
		);
	}

	private void assertQueryError(
		ThrowingCallable invocation,
		PredictionLeaderboardQueryErrorCode expectedErrorCode
	) {
		assertThatThrownBy(invocation)
			.isInstanceOfSatisfying(
				PredictionLeaderboardQueryException.class,
				exception -> assertThat(exception.getErrorCode())
					.isEqualTo(expectedErrorCode)
			);
	}

	private PredictionLeaderboardEntry entry(long rank, long memberId) {
		return new PredictionLeaderboardEntry(
			rank,
			memberId,
			"member-" + memberId,
			3,
			1_500L,
			3_000L,
			new BigDecimal("200.0")
		);
	}
}
