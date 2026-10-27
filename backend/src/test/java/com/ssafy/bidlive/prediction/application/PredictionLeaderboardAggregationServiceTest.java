package com.ssafy.bidlive.prediction.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

/** 일일 집계 서비스가 서울 날짜 경계와 계산 시각을 Port에 전달하는지 검증한다. */
class PredictionLeaderboardAggregationServiceTest {

	private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

	private final PredictionLeaderboardAggregationPersistence persistence =
		mock(PredictionLeaderboardAggregationPersistence.class);
	private final ApplicationEventPublisher eventPublisher =
		mock(ApplicationEventPublisher.class);

	@Test
	void aggregatesOneSeoulCalendarDay() {
		Clock clock = Clock.fixed(
			Instant.parse("2026-07-19T03:04:05.123456789Z"),
			SEOUL_ZONE
		);
		PredictionLeaderboardAggregationService service =
			new PredictionLeaderboardAggregationService(
				persistence,
				eventPublisher,
				clock
			);
		LocalDate rankingDate = LocalDate.of(2026, 7, 18);
		OffsetDateTime calculatedAt = OffsetDateTime.parse(
			"2026-07-19T12:04:05.123456+09:00"
		);
		PredictionLeaderboardSnapshot expected =
			new PredictionLeaderboardSnapshot(
				rankingDate,
				calculatedAt,
				List.of()
			);
		when(persistence.replaceSnapshot(
			rankingDate,
			OffsetDateTime.parse("2026-07-18T00:00:00+09:00"),
			OffsetDateTime.parse("2026-07-19T00:00:00+09:00"),
			calculatedAt
		)).thenReturn(expected);

		PredictionLeaderboardSnapshot result =
			service.aggregate(rankingDate);

		assertThat(result).isSameAs(expected);
		verify(persistence).replaceSnapshot(
			rankingDate,
			OffsetDateTime.parse("2026-07-18T00:00:00+09:00"),
			OffsetDateTime.parse("2026-07-19T00:00:00+09:00"),
			calculatedAt
		);
		verify(eventPublisher).publishEvent(
			new PredictionLeaderboardAggregatedEvent(
				rankingDate,
				calculatedAt
			)
		);
	}

	@Test
	void rejectsNullRankingDate() {
		PredictionLeaderboardAggregationService service =
			new PredictionLeaderboardAggregationService(
				persistence,
				eventPublisher,
				Clock.system(SEOUL_ZONE)
			);

		assertThatThrownBy(() -> service.aggregate(null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("rankingDate must not be null");
		verifyNoInteractions(persistence);
		verifyNoInteractions(eventPublisher);
	}
}
