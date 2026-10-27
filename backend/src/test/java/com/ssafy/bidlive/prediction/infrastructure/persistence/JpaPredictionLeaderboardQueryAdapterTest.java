package com.ssafy.bidlive.prediction.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.ssafy.bidlive.prediction.application.PredictionLeaderboardCursor;
import com.ssafy.bidlive.prediction.application.PredictionLeaderboardEntry;
import com.ssafy.bidlive.prediction.application.PredictionLeaderboardPage;
import com.ssafy.bidlive.prediction.application.PredictionLeaderboardTop10Snapshot;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** DB 읽기 모델의 size+1 커서 페이지와 회원별 단건 조건을 검증한다. */
@ExtendWith(MockitoExtension.class)
class JpaPredictionLeaderboardQueryAdapterTest {

	private static final LocalDate RANKING_DATE =
		LocalDate.parse("2026-07-22");
	private static final OffsetDateTime CALCULATED_AT =
		OffsetDateTime.parse("2026-07-23T00:05:03+09:00");

	@Mock
	private EntityManager entityManager;

	@Mock
	private Query query;

	private JpaPredictionLeaderboardQueryAdapter adapter;

	@BeforeEach
	void setUp() {
		adapter = new JpaPredictionLeaderboardQueryAdapter(entityManager);
		when(entityManager.createNativeQuery(anyString())).thenReturn(query);
		when(query.setParameter(anyString(), any())).thenReturn(query);
		when(query.setMaxResults(anyInt())).thenReturn(query);
	}

	@Test
	void returnsRequestedFirstPageAndBuildsNextCursor() {
		List<Object[]> rows = rows(1L, 11L);
		when(query.getResultList()).thenReturn(rows);

		PredictionLeaderboardPage result = adapter.findPage(
			RANKING_DATE,
			null,
			10
		);

		assertThat(result.items()).hasSize(10);
		assertThat(result.items()).extracting(
			PredictionLeaderboardEntry::rank
		).containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L);
		assertThat(result.hasNext()).isTrue();
		assertThat(PredictionLeaderboardCursor.decode(result.nextCursor()))
			.isEqualTo(new PredictionLeaderboardCursor(
				RANKING_DATE,
				CALCULATED_AT,
				10L,
				110L
			));
		verify(query).setMaxResults(11);
	}

	@Test
	void appliesVersionedRankConditionToCursorPage() {
		PredictionLeaderboardCursor cursor =
			new PredictionLeaderboardCursor(
				RANKING_DATE,
				CALCULATED_AT,
				10L,
				110L
			);
		when(query.getResultList()).thenReturn(rows(11L, 12L));

		PredictionLeaderboardPage result = adapter.findPage(
			RANKING_DATE,
			cursor,
			20
		);

		assertThat(result.items()).extracting(
			PredictionLeaderboardEntry::rank
		).containsExactly(11L, 12L);
		assertThat(result.hasNext()).isFalse();
		verify(query).setParameter("calculatedAt", CALCULATED_AT);
		verify(query).setParameter("afterRank", 10L);
		verify(query).setMaxResults(21);
		ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
		verify(entityManager).createNativeQuery(sql.capture());
		assertThat(sql.getValue())
			.contains("leaderboard.created_at = :calculatedAt")
			.contains("leaderboard.rank_no > :afterRank");
	}

	@Test
	void returnsEmptyPageWithoutCalculatedAtWhenNoRowsExist() {
		when(query.getResultList()).thenReturn(List.of());

		PredictionLeaderboardPage result = adapter.findPage(
			RANKING_DATE,
			null,
			10
		);

		assertThat(result.calculatedAt()).isNull();
		assertThat(result.items()).isEmpty();
		assertThat(result.nextCursor()).isNull();
		assertThat(result.hasNext()).isFalse();
	}

	@Test
	void returnsOnlyTenEntriesForAggregationCacheRefresh() {
		when(query.getResultList()).thenReturn(rows(1L, 11L));

		PredictionLeaderboardTop10Snapshot result = adapter.findTop10(
			RANKING_DATE,
			CALCULATED_AT
		);

		assertThat(result.items()).hasSize(10);
		assertThat(result.hasNext()).isTrue();
		verify(query).setMaxResults(11);
	}

	@Test
	void findsMyRankByDateVersionAndMemberId() {
		when(query.getResultList()).thenReturn(
			Collections.singletonList(row(18L, 25L))
		);

		assertThat(adapter.findMyRank(
			RANKING_DATE,
			CALCULATED_AT,
			25L
		)).get().satisfies(entry -> {
			assertThat(entry.rank()).isEqualTo(18L);
			assertThat(entry.memberId()).isEqualTo(25L);
			assertThat(entry.payoutRate()).isEqualByComparingTo("200.0");
		});
		verify(query).setParameter("rankingDate", RANKING_DATE);
		verify(query).setParameter("calculatedAt", CALCULATED_AT);
		verify(query).setParameter("memberId", 25L);
		ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
		verify(entityManager).createNativeQuery(sql.capture());
		assertThat(sql.getValue())
			.contains("leaderboard.created_at = :calculatedAt")
			.contains("leaderboard.member_id = :memberId");
	}

	private List<Object[]> rows(long firstRank, long lastRank) {
		List<Object[]> rows = new ArrayList<>();
		for (long rank = firstRank; rank <= lastRank; rank++) {
			rows.add(row(rank, rank + 100));
		}
		return rows;
	}

	private Object[] row(long rank, long memberId) {
		return new Object[] {
			rank,
			memberId,
			"member-" + memberId,
			3,
			1_500L,
			3_000L,
			CALCULATED_AT
		};
	}
}
