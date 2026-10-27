package com.ssafy.bidlive.prediction.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import com.ssafy.bidlive.prediction.application.PredictionLeaderboardAggregationService;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

/** 서울 날짜 경계와 매일 00시 05분의 전날 리더보드 집계 실행을 검증한다. */
class PredictionLeaderboardAggregationSchedulerTest {

	private static final Instant SEOUL_MIDNIGHT_FIVE =
		Instant.parse("2026-07-22T15:05:00Z");

	@Test
	void aggregatesPreviousDayBasedOnSeoulDate() {
		PredictionLeaderboardAggregationService aggregationService = mock(
			PredictionLeaderboardAggregationService.class
		);
		Clock utcClock = Clock.fixed(SEOUL_MIDNIGHT_FIVE, ZoneOffset.UTC);
		PredictionLeaderboardAggregationScheduler scheduler =
			new PredictionLeaderboardAggregationScheduler(
				aggregationService,
				utcClock
			);

		scheduler.aggregatePreviousDay();

		verify(aggregationService).aggregate(LocalDate.of(2026, 7, 22));
		verifyNoMoreInteractions(aggregationService);
	}

	@Test
	void runsEveryDayAtMidnightFiveInSeoul() throws NoSuchMethodException {
		Method scheduledMethod = PredictionLeaderboardAggregationScheduler.class
			.getDeclaredMethod("aggregatePreviousDay");
		Scheduled scheduled = scheduledMethod.getAnnotation(Scheduled.class);

		assertThat(scheduled).isNotNull();
		assertThat(scheduled.cron()).isEqualTo("0 5 0 * * *");
		assertThat(scheduled.zone()).isEqualTo("Asia/Seoul");
	}
}
