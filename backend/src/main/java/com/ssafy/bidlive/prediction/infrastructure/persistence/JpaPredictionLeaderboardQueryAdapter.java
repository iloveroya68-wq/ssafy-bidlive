package com.ssafy.bidlive.prediction.infrastructure.persistence;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.ssafy.bidlive.prediction.application.PredictionLeaderboardCursor;
import com.ssafy.bidlive.prediction.application.PredictionLeaderboardEntry;
import com.ssafy.bidlive.prediction.application.PredictionLeaderboardPage;
import com.ssafy.bidlive.prediction.application.PredictionLeaderboardQueryPersistence;
import com.ssafy.bidlive.prediction.application.PredictionLeaderboardTop10Snapshot;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;

/** 확정된 DB 읽기 모델에서 커서 페이지와 로그인 회원의 순위를 조회한다. */
@Repository
public class JpaPredictionLeaderboardQueryAdapter
	implements PredictionLeaderboardQueryPersistence {

	private static final int TOP10_SIZE = 10;
	private static final int MAX_PAGE_SIZE = 100;
	private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

	private static final String SELECT_ENTRY = """
		SELECT
			leaderboard.rank_no,
			leaderboard.member_id,
			member.nickname,
			leaderboard.prediction_count,
			leaderboard.total_stake_amount,
			leaderboard.total_payout_amount,
			leaderboard.created_at
		FROM prediction_leaderboard leaderboard
		JOIN member
			ON member.member_id = leaderboard.member_id
		WHERE leaderboard.ranking_date = :rankingDate
		""";

	private static final String SELECT_FIRST_PAGE = SELECT_ENTRY + """
		ORDER BY leaderboard.rank_no ASC
		""";

	private static final String SELECT_CURSOR_PAGE = SELECT_ENTRY + """
			AND leaderboard.created_at = :calculatedAt
			AND leaderboard.rank_no > :afterRank
		ORDER BY leaderboard.rank_no ASC
		""";

	private static final String SELECT_TOP10 = SELECT_ENTRY + """
			AND leaderboard.created_at = :calculatedAt
		ORDER BY leaderboard.rank_no ASC
		""";

	private static final String SELECT_MY_RANK = SELECT_ENTRY + """
			AND leaderboard.created_at = :calculatedAt
			AND leaderboard.member_id = :memberId
		""";

	private final EntityManager entityManager;

	public JpaPredictionLeaderboardQueryAdapter(EntityManager entityManager) {
		this.entityManager = entityManager;
	}

	@Override
	public PredictionLeaderboardPage findPage(
		LocalDate rankingDate,
		PredictionLeaderboardCursor cursor,
		int size
	) {
		validatePageRequest(rankingDate, cursor, size);
		String sql = cursor == null ? SELECT_FIRST_PAGE : SELECT_CURSOR_PAGE;
		Query query = entityManager.createNativeQuery(sql)
			.setParameter("rankingDate", rankingDate);
		if (cursor != null) {
			query.setParameter("calculatedAt", cursor.calculatedAt())
				.setParameter("afterRank", cursor.rank());
		}
		query.setMaxResults(size + 1);

		List<?> rows = query.getResultList();
		if (rows.isEmpty()) {
			return new PredictionLeaderboardPage(
				rankingDate,
				null,
				List.of(),
				null,
				false
			);
		}

		OffsetDateTime calculatedAt = calculatedAtOf(rows.getFirst());
		List<PredictionLeaderboardEntry> entries = new ArrayList<>(
			rows.stream()
				.map(JpaPredictionLeaderboardQueryAdapter::toEntry)
				.toList()
		);
		boolean hasNext = entries.size() > size;
		if (hasNext) {
			entries = new ArrayList<>(entries.subList(0, size));
		}
		String nextCursor = hasNext
			? cursorOf(rankingDate, calculatedAt, entries.getLast()).encode()
			: null;
		return new PredictionLeaderboardPage(
			rankingDate,
			calculatedAt,
			entries,
			nextCursor,
			hasNext
		);
	}

	@Override
	public PredictionLeaderboardTop10Snapshot findTop10(
		LocalDate rankingDate,
		OffsetDateTime calculatedAt
	) {
		Query query = entityManager.createNativeQuery(SELECT_TOP10)
			.setParameter("rankingDate", rankingDate)
			.setParameter("calculatedAt", calculatedAt);
		query.setMaxResults(TOP10_SIZE + 1);

		List<?> rows = query.getResultList();
		List<PredictionLeaderboardEntry> entries = new ArrayList<>(
			rows.stream()
				.map(JpaPredictionLeaderboardQueryAdapter::toEntry)
				.toList()
		);
		boolean hasNext = entries.size() > TOP10_SIZE;
		if (hasNext) {
			entries = new ArrayList<>(entries.subList(0, TOP10_SIZE));
		}
		String nextCursor = hasNext
			? cursorOf(rankingDate, calculatedAt, entries.getLast()).encode()
			: null;
		return new PredictionLeaderboardTop10Snapshot(
			rankingDate,
			calculatedAt,
			entries,
			nextCursor,
			hasNext
		);
	}

	@Override
	public Optional<PredictionLeaderboardEntry> findMyRank(
		LocalDate rankingDate,
		OffsetDateTime calculatedAt,
		long memberId
	) {
		List<?> rows = entityManager.createNativeQuery(SELECT_MY_RANK)
			.setParameter("rankingDate", rankingDate)
			.setParameter("calculatedAt", calculatedAt)
			.setParameter("memberId", memberId)
			.setMaxResults(1)
			.getResultList();
		return rows.stream().findFirst()
			.map(JpaPredictionLeaderboardQueryAdapter::toEntry);
	}

	private static void validatePageRequest(
		LocalDate rankingDate,
		PredictionLeaderboardCursor cursor,
		int size
	) {
		if (rankingDate == null || size < 1 || size > MAX_PAGE_SIZE) {
			throw new IllegalArgumentException("invalid leaderboard page request");
		}
		if (cursor != null && !cursor.rankingDate().equals(rankingDate)) {
			throw new IllegalArgumentException(
				"cursor rankingDate does not match request"
			);
		}
	}

	private static PredictionLeaderboardCursor cursorOf(
		LocalDate rankingDate,
		OffsetDateTime calculatedAt,
		PredictionLeaderboardEntry entry
	) {
		return new PredictionLeaderboardCursor(
			rankingDate,
			calculatedAt,
			entry.rank(),
			entry.memberId()
		);
	}

	private static OffsetDateTime calculatedAtOf(Object rowValue) {
		Object[] row = (Object[]) rowValue;
		Object calculatedAt = row[6];
		if (calculatedAt instanceof OffsetDateTime offsetDateTime) {
			return offsetDateTime;
		}
		if (calculatedAt instanceof Instant instant) {
			return instant.atZone(SEOUL_ZONE).toOffsetDateTime();
		}
		throw new IllegalArgumentException(
			"unsupported leaderboard calculatedAt type"
		);
	}

	private static PredictionLeaderboardEntry toEntry(Object rowValue) {
		Object[] row = (Object[]) rowValue;
		long stakedPoints = asLong(row[4]);
		long payoutPoints = asLong(row[5]);
		return new PredictionLeaderboardEntry(
			asLong(row[0]),
			asLong(row[1]),
			(String) row[2],
			((Number) row[3]).intValue(),
			stakedPoints,
			payoutPoints,
			payoutRate(stakedPoints, payoutPoints)
		);
	}

	private static BigDecimal payoutRate(
		long stakedPoints,
		long payoutPoints
	) {
		return BigDecimal.valueOf(payoutPoints)
			.multiply(BigDecimal.valueOf(100))
			.divide(
				BigDecimal.valueOf(stakedPoints),
				1,
				RoundingMode.HALF_UP
			);
	}

	private static long asLong(Object value) {
		return ((Number) value).longValue();
	}
}
