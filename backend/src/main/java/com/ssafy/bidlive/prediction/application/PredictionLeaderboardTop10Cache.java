package com.ssafy.bidlive.prediction.application;

import java.time.LocalDate;
import java.util.Optional;

/** 날짜별 상위 10명 캐시의 조회와 교체를 추상화하는 Port다. */
public interface PredictionLeaderboardTop10Cache {

	Optional<PredictionLeaderboardTop10Snapshot> find(LocalDate rankingDate);

	void save(PredictionLeaderboardTop10Snapshot snapshot);
}
