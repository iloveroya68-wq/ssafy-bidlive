package com.ssafy.bidlive.prediction.application;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import com.ssafy.bidlive.prediction.domain.PredictionLeaderboardQueryErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** 조회 조건에 따라 상위 10명 캐시 또는 DB 커서 페이지와 본인 순위를 결합한다. */
@Service
public class PredictionLeaderboardQueryService {

	private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
	private static final int DEFAULT_PAGE_SIZE = 10;
	private static final int MAX_PAGE_SIZE = 100;

	private final PredictionLeaderboardTop10Cache cache;
	private final PredictionLeaderboardQueryPersistence persistence;
	private final Clock clock;

	@Autowired
	public PredictionLeaderboardQueryService(
		PredictionLeaderboardTop10Cache cache,
		PredictionLeaderboardQueryPersistence persistence
	) {
		this(cache, persistence, Clock.system(SEOUL_ZONE));
	}

	PredictionLeaderboardQueryService(
		PredictionLeaderboardTop10Cache cache,
		PredictionLeaderboardQueryPersistence persistence,
		Clock clock
	) {
		this.cache = cache;
		this.persistence = persistence;
		this.clock = clock;
	}

	public PredictionLeaderboardQueryResult query(
		LocalDate requestedDate,
		int size,
		String encodedCursor,
		long memberId
	) {
		LocalDate rankingDate = resolveRankingDate(requestedDate);
		validate(size, memberId);
		PredictionLeaderboardCursor cursor = decodeCursor(
			encodedCursor,
			rankingDate
		);

		if (cursor == null && size == DEFAULT_PAGE_SIZE) {
			return queryTop10(rankingDate, memberId);
		}

		PredictionLeaderboardPage page = persistence.findPage(
			rankingDate,
			cursor,
			size
		);
		validateSnapshotCursor(cursor, page);
		return combineWithMyRank(page, memberId);
	}

	public PredictionLeaderboardTop10Snapshot refreshTop10(
		LocalDate rankingDate,
		OffsetDateTime calculatedAt
	) {
		validateSnapshot(rankingDate, calculatedAt);
		return loadAndCache(rankingDate, calculatedAt);
	}

	private PredictionLeaderboardQueryResult queryTop10(
		LocalDate rankingDate,
		long memberId
	) {
		PredictionLeaderboardTop10Snapshot cached = cache.find(
			rankingDate
		).orElse(null);
		if (cached != null) {
			return combineWithMyRank(cached, memberId);
		}

		PredictionLeaderboardPage page = persistence.findPage(
			rankingDate,
			null,
			DEFAULT_PAGE_SIZE
		);
		if (page.calculatedAt() != null) {
			cache.save(toTop10Snapshot(page));
		}
		return combineWithMyRank(page, memberId);
	}

	private PredictionLeaderboardQueryResult combineWithMyRank(
		PredictionLeaderboardPage page,
		long memberId
	) {
		if (page.calculatedAt() == null) {
			return emptyResult(page.rankingDate());
		}
		PredictionLeaderboardEntry myRank = persistence.findMyRank(
			page.rankingDate(),
			page.calculatedAt(),
			memberId
		).orElse(null);
		return new PredictionLeaderboardQueryResult(
			page.rankingDate(),
			page.calculatedAt(),
			page.items(),
			myRank,
			page.nextCursor(),
			page.hasNext()
		);
	}

	private PredictionLeaderboardQueryResult combineWithMyRank(
		PredictionLeaderboardTop10Snapshot snapshot,
		long memberId
	) {
		PredictionLeaderboardEntry myRank = persistence.findMyRank(
			snapshot.rankingDate(),
			snapshot.calculatedAt(),
			memberId
		).orElse(null);
		return new PredictionLeaderboardQueryResult(
			snapshot.rankingDate(),
			snapshot.calculatedAt(),
			snapshot.items(),
			myRank,
			snapshot.nextCursor(),
			snapshot.hasNext()
		);
	}

	private PredictionLeaderboardTop10Snapshot loadAndCache(
		LocalDate rankingDate,
		OffsetDateTime calculatedAt
	) {
		PredictionLeaderboardTop10Snapshot snapshot = persistence.findTop10(
			rankingDate,
			calculatedAt
		);
		cache.save(snapshot);
		return snapshot;
	}

	private LocalDate resolveRankingDate(LocalDate requestedDate) {
		LocalDate today = LocalDate.now(clock.withZone(SEOUL_ZONE));
		LocalDate rankingDate = requestedDate == null
			? today.minusDays(1)
			: requestedDate;
		if (!rankingDate.isBefore(today)) {
			throw new PredictionLeaderboardQueryException(
				PredictionLeaderboardQueryErrorCode.INVALID_RANKING_DATE
			);
		}
		return rankingDate;
	}

	private void validate(int size, long memberId) {
		if (size < 1 || size > MAX_PAGE_SIZE) {
			throw new PredictionLeaderboardQueryException(
				PredictionLeaderboardQueryErrorCode.INVALID_PAGE_SIZE
			);
		}
		if (memberId <= 0) {
			throw new PredictionLeaderboardQueryException(
				PredictionLeaderboardQueryErrorCode.UNAUTHORIZED
			);
		}
	}

	private PredictionLeaderboardCursor decodeCursor(
		String encodedCursor,
		LocalDate rankingDate
	) {
		if (encodedCursor == null) {
			return null;
		}
		if (encodedCursor.isBlank()) {
			throw new PredictionLeaderboardQueryException(
				PredictionLeaderboardQueryErrorCode.INVALID_CURSOR
			);
		}
		PredictionLeaderboardCursor cursor;
		try {
			cursor = PredictionLeaderboardCursor.decode(encodedCursor);
		} catch (IllegalArgumentException exception) {
			throw new PredictionLeaderboardQueryException(
				PredictionLeaderboardQueryErrorCode.INVALID_CURSOR,
				exception
			);
		}
		if (!cursor.rankingDate().equals(rankingDate)) {
			throw new PredictionLeaderboardQueryException(
				PredictionLeaderboardQueryErrorCode.INVALID_CURSOR
			);
		}
		return cursor;
	}

	private void validateSnapshotCursor(
		PredictionLeaderboardCursor cursor,
		PredictionLeaderboardPage page
	) {
		if (cursor != null
			&& page.calculatedAt() != null
			&& !cursor.calculatedAt().isEqual(page.calculatedAt())) {
			throw new PredictionLeaderboardQueryException(
				PredictionLeaderboardQueryErrorCode.INVALID_CURSOR
			);
		}
	}

	private PredictionLeaderboardTop10Snapshot toTop10Snapshot(
		PredictionLeaderboardPage page
	) {
		return new PredictionLeaderboardTop10Snapshot(
			page.rankingDate(),
			page.calculatedAt(),
			page.items(),
			page.nextCursor(),
			page.hasNext()
		);
	}

	private PredictionLeaderboardQueryResult emptyResult(
		LocalDate rankingDate
	) {
		return new PredictionLeaderboardQueryResult(
			rankingDate,
			null,
			List.of(),
			null,
			null,
			false
		);
	}

	private void validateSnapshot(
		LocalDate rankingDate,
		OffsetDateTime calculatedAt
	) {
		if (rankingDate == null || calculatedAt == null) {
			throw new IllegalArgumentException(
				"rankingDate and calculatedAt must not be null"
			);
		}
	}
}
