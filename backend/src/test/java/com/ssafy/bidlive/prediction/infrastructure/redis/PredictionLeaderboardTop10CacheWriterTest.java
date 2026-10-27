package com.ssafy.bidlive.prediction.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import com.ssafy.bidlive.prediction.application.PredictionLeaderboardAggregatedEvent;
import com.ssafy.bidlive.prediction.application.PredictionLeaderboardQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 일일 집계 캐시 갱신이 커밋 이후 실행되고 실패를 격리하는지 검증한다. */
class PredictionLeaderboardTop10CacheWriterTest {

	private static final LocalDate RANKING_DATE =
		LocalDate.parse("2026-07-22");
	private static final OffsetDateTime CALCULATED_AT =
		OffsetDateTime.parse("2026-07-23T00:05:03+09:00");

	private final PredictionLeaderboardQueryService queryService =
		mock(PredictionLeaderboardQueryService.class);
	private final PredictionLeaderboardTop10CacheWriter writer =
		new PredictionLeaderboardTop10CacheWriter(queryService);

	@Test
	void refreshesTop10AfterAggregationCommit() {
		writer.onLeaderboardAggregated(event());

		verify(queryService).refreshTop10(RANKING_DATE, CALCULATED_AT);
	}

	@Test
	void receivesAggregationOnlyAfterCommit() throws NoSuchMethodException {
		TransactionalEventListener listener =
			PredictionLeaderboardTop10CacheWriter.class
				.getMethod(
					"onLeaderboardAggregated",
					PredictionLeaderboardAggregatedEvent.class
				)
				.getAnnotation(TransactionalEventListener.class);

		assertThat(listener).isNotNull();
		assertThat(listener.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
	}

	@Test
	void doesNotPropagateRefreshFailureAfterDatabaseCommit() {
		doThrow(new IllegalStateException("redis unavailable"))
			.when(queryService)
			.refreshTop10(RANKING_DATE, CALCULATED_AT);

		assertThatCode(() -> writer.onLeaderboardAggregated(event()))
			.doesNotThrowAnyException();
	}

	private PredictionLeaderboardAggregatedEvent event() {
		return new PredictionLeaderboardAggregatedEvent(
			RANKING_DATE,
			CALCULATED_AT
		);
	}
}
