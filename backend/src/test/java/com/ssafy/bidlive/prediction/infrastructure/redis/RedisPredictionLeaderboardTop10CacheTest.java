package com.ssafy.bidlive.prediction.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.bidlive.prediction.application.PredictionLeaderboardEntry;
import com.ssafy.bidlive.prediction.application.PredictionLeaderboardTop10Snapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/** 상위 10명 JSON 캐시의 TTL·날짜 검사·Redis 장애 fallback을 검증한다. */
@ExtendWith(MockitoExtension.class)
class RedisPredictionLeaderboardTop10CacheTest {

	private static final String KEY =
		"prediction:leaderboard:daily:2026-07-22:top10";
	private static final LocalDate RANKING_DATE =
		LocalDate.parse("2026-07-22");
	private static final OffsetDateTime CALCULATED_AT =
		OffsetDateTime.parse("2026-07-23T00:05:03+09:00");

	@Mock
	private StringRedisTemplate redisTemplate;

	@Mock
	private ValueOperations<String, String> valueOperations;

	private ObjectMapper objectMapper;
	private RedisPredictionLeaderboardTop10Cache cache;

	@BeforeEach
	void setUp() {
		objectMapper = new ObjectMapper().findAndRegisterModules();
		cache = new RedisPredictionLeaderboardTop10Cache(
			redisTemplate,
			objectMapper
		);
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
	}

	@Test
	void storesJsonWithFortyEightHourTtlAndReadsItBack() throws Exception {
		PredictionLeaderboardTop10Snapshot snapshot = snapshot(RANKING_DATE);

		cache.save(snapshot);

		ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
		verify(valueOperations).set(
			org.mockito.ArgumentMatchers.eq(KEY),
			json.capture(),
			org.mockito.ArgumentMatchers.eq(Duration.ofHours(48))
		);
		when(valueOperations.get(KEY)).thenReturn(json.getValue());
		assertThat(cache.find(RANKING_DATE)).contains(snapshot);
	}

	@Test
	void rejectsPayloadForDifferentRankingDate() throws Exception {
		when(valueOperations.get(KEY)).thenReturn(
			objectMapper.writeValueAsString(
				snapshot(RANKING_DATE.minusDays(1))
			)
		);

		assertThat(cache.find(RANKING_DATE)).isEmpty();
	}

	@Test
	void turnsRedisReadFailureIntoCacheMiss() {
		when(valueOperations.get(KEY)).thenThrow(
			new RedisConnectionFailureException("redis unavailable")
		);

		assertThat(cache.find(RANKING_DATE)).isEmpty();
	}

	@Test
	void doesNotPropagateRedisWriteFailure() {
		org.mockito.Mockito.doThrow(
			new RedisConnectionFailureException("redis unavailable")
		).when(valueOperations).set(
			org.mockito.ArgumentMatchers.eq(KEY),
			anyString(),
			org.mockito.ArgumentMatchers.eq(Duration.ofHours(48))
		);

		assertThatCode(() ->
			cache.save(snapshot(RANKING_DATE))
		).doesNotThrowAnyException();
	}

	private PredictionLeaderboardTop10Snapshot snapshot(
		LocalDate rankingDate
	) {
		return new PredictionLeaderboardTop10Snapshot(
			rankingDate,
			CALCULATED_AT,
			List.of(new PredictionLeaderboardEntry(
				1L,
				25L,
				"시세예언자",
				4,
				2_000L,
				8_000L,
				new BigDecimal("400.0")
			)),
			null,
			false
		);
	}
}
