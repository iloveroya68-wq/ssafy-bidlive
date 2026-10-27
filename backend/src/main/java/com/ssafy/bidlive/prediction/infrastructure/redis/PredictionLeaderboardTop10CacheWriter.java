package com.ssafy.bidlive.prediction.infrastructure.redis;

import com.ssafy.bidlive.prediction.application.PredictionLeaderboardAggregatedEvent;
import com.ssafy.bidlive.prediction.application.PredictionLeaderboardQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 일일 집계 커밋 후 해당 날짜의 상위 10명 Redis 캐시를 갱신한다. */
@Component
public class PredictionLeaderboardTop10CacheWriter {

	private static final Logger log = LoggerFactory.getLogger(
		PredictionLeaderboardTop10CacheWriter.class
	);

	private final PredictionLeaderboardQueryService queryService;

	public PredictionLeaderboardTop10CacheWriter(
		PredictionLeaderboardQueryService queryService
	) {
		this.queryService = queryService;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onLeaderboardAggregated(
		PredictionLeaderboardAggregatedEvent event
	) {
		try {
			queryService.refreshTop10(
				event.rankingDate(),
				event.calculatedAt()
			);
		} catch (RuntimeException exception) {
			log.warn(
				"Prediction leaderboard cache refresh failed: rankingDate={}, failureType={}",
				event.rankingDate(),
				exception.getClass().getSimpleName()
			);
		}
	}
}
