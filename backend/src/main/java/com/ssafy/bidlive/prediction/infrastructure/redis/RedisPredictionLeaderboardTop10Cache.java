package com.ssafy.bidlive.prediction.infrastructure.redis;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.bidlive.prediction.application.PredictionLeaderboardTop10Cache;
import com.ssafy.bidlive.prediction.application.PredictionLeaderboardTop10Snapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/** 날짜별 상위 10명 리더보드를 JSON Value와 48시간 TTL로 캐시한다. */
@Repository
public class RedisPredictionLeaderboardTop10Cache
	implements PredictionLeaderboardTop10Cache {

	private static final Logger log = LoggerFactory.getLogger(
		RedisPredictionLeaderboardTop10Cache.class
	);
	private static final Duration CACHE_TTL = Duration.ofHours(48);
	private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

	private final StringRedisTemplate redisTemplate;
	private final ObjectMapper objectMapper;

	public RedisPredictionLeaderboardTop10Cache(
		StringRedisTemplate redisTemplate,
		ObjectMapper objectMapper
	) {
		this.redisTemplate = redisTemplate;
		this.objectMapper = objectMapper.copy().disable(
			DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE
		);
	}

	@Override
	public Optional<PredictionLeaderboardTop10Snapshot> find(
		LocalDate rankingDate
	) {
		String key = PredictionLeaderboardRedisKey.top10(rankingDate);
		try {
			String value = redisTemplate.opsForValue().get(key);
			if (value == null) {
				return Optional.empty();
			}
			PredictionLeaderboardTop10Snapshot snapshot = objectMapper.readValue(
				value,
				PredictionLeaderboardTop10Snapshot.class
			);
			if (!snapshot.rankingDate().equals(rankingDate)) {
				return Optional.empty();
			}
			OffsetDateTime calculatedAt = snapshot.calculatedAt()
				.atZoneSameInstant(SEOUL_ZONE)
				.toOffsetDateTime();
			return Optional.of(new PredictionLeaderboardTop10Snapshot(
				snapshot.rankingDate(),
				calculatedAt,
				snapshot.items(),
				snapshot.nextCursor(),
				snapshot.hasNext()
			));
		} catch (DataAccessException | JsonProcessingException exception) {
			log.warn(
				"Prediction leaderboard cache read failed: key={}, failureType={}",
				key,
				exception.getClass().getSimpleName()
			);
			return Optional.empty();
		}
	}

	@Override
	public void save(PredictionLeaderboardTop10Snapshot snapshot) {
		String key = PredictionLeaderboardRedisKey.top10(
			snapshot.rankingDate()
		);
		try {
			String value = objectMapper.writeValueAsString(snapshot);
			redisTemplate.opsForValue().set(key, value, CACHE_TTL);
		} catch (DataAccessException | JsonProcessingException exception) {
			log.warn(
				"Prediction leaderboard cache write failed: key={}, failureType={}",
				key,
				exception.getClass().getSimpleName()
			);
		}
	}
}
