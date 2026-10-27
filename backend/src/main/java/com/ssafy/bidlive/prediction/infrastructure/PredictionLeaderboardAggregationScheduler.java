package com.ssafy.bidlive.prediction.infrastructure;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;

import com.ssafy.bidlive.prediction.application.PredictionLeaderboardAggregationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 서울 기준 매일 자정 이후 전날의 예측 리더보드 집계를 시작한다. */
@Component
public class PredictionLeaderboardAggregationScheduler {

	private static final String SEOUL_ZONE_ID = "Asia/Seoul";
	private static final ZoneId SEOUL_ZONE = ZoneId.of(SEOUL_ZONE_ID);

	private final PredictionLeaderboardAggregationService aggregationService;
	private final Clock clock;

	@Autowired
	public PredictionLeaderboardAggregationScheduler(
		PredictionLeaderboardAggregationService aggregationService
	) {
		this(aggregationService, Clock.system(SEOUL_ZONE));
	}

	PredictionLeaderboardAggregationScheduler(
		PredictionLeaderboardAggregationService aggregationService,
		Clock clock
	) {
		this.aggregationService = Objects.requireNonNull(
			aggregationService,
			"aggregationService"
		);
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	@Scheduled(cron = "0 5 0 * * *", zone = SEOUL_ZONE_ID)
	void aggregatePreviousDay() {
		LocalDate rankingDate = LocalDate.now(clock.withZone(SEOUL_ZONE))
			.minusDays(1);
		aggregationService.aggregate(rankingDate);
	}
}
