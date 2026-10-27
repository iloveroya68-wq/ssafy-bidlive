package com.ssafy.bidlive.prediction.application;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 서울 날짜 경계를 계산해 확정된 하루의 예측 리더보드를 원자적으로 집계한다. */
@Service
public class PredictionLeaderboardAggregationService {

	private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

	private final PredictionLeaderboardAggregationPersistence persistence;
	private final ApplicationEventPublisher eventPublisher;
	private final Clock clock;

	@Autowired
	public PredictionLeaderboardAggregationService(
		PredictionLeaderboardAggregationPersistence persistence,
		ApplicationEventPublisher eventPublisher
	) {
		this(persistence, eventPublisher, Clock.system(SEOUL_ZONE));
	}

	PredictionLeaderboardAggregationService(
		PredictionLeaderboardAggregationPersistence persistence,
		ApplicationEventPublisher eventPublisher,
		Clock clock
	) {
		this.persistence = persistence;
		this.eventPublisher = eventPublisher;
		this.clock = clock;
	}

	@Transactional
	public PredictionLeaderboardSnapshot aggregate(LocalDate rankingDate) {
		if (rankingDate == null) {
			throw new IllegalArgumentException(
				"rankingDate must not be null"
			);
		}

		OffsetDateTime rangeStart = rankingDate
			.atStartOfDay(SEOUL_ZONE)
			.toOffsetDateTime();
		OffsetDateTime rangeEnd = rankingDate
			.plusDays(1)
			.atStartOfDay(SEOUL_ZONE)
			.toOffsetDateTime();
		OffsetDateTime calculatedAt = clock.instant()
			.truncatedTo(ChronoUnit.MICROS)
			.atZone(SEOUL_ZONE)
			.toOffsetDateTime();

		PredictionLeaderboardSnapshot snapshot = persistence.replaceSnapshot(
			rankingDate,
			rangeStart,
			rangeEnd,
			calculatedAt
		);
		eventPublisher.publishEvent(
			new PredictionLeaderboardAggregatedEvent(
				snapshot.rankingDate(),
				snapshot.calculatedAt()
			)
		);
		return snapshot;
	}
}
