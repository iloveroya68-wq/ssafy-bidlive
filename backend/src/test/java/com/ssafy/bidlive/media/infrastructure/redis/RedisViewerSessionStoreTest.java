package com.ssafy.bidlive.media.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import com.ssafy.bidlive.media.infrastructure.ViewerSessionProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.RedisScript;

/** STOMP 세션 등록과 제거가 경매별 Redis ZSET 명령으로 변환되는지 검증합니다. */
@ExtendWith(MockitoExtension.class)
class RedisViewerSessionStoreTest {

	@Mock
	private StringRedisTemplate redisTemplate;

	@Mock
	private ZSetOperations<String, String> zSetOperations;

	private RedisViewerSessionStore store;

	@BeforeEach
	void setUp() {
		store = new RedisViewerSessionStore(
			redisTemplate,
			new ViewerSessionProperties()
		);
	}

	@Test
	void recordsSessionWithLastSeenEpochMillis() {
		Instant lastSeenAt = Instant.parse("2026-07-29T08:30:00Z");
		when(redisTemplate.execute(
			any(RedisScript.class),
			eq(List.of("bidlive:media:{42}:viewer-sessions")),
			eq(Long.toString(lastSeenAt.toEpochMilli())),
			eq("stomp-session-1"),
			eq("40000")
		)).thenReturn(1L);

		store.record(42L, "stomp-session-1", lastSeenAt);

		verify(redisTemplate).execute(
			any(RedisScript.class),
			eq(List.of("bidlive:media:{42}:viewer-sessions")),
			eq(Long.toString(lastSeenAt.toEpochMilli())),
			eq("stomp-session-1"),
			eq("40000")
		);
	}

	@Test
	void removesSessionFromAuctionSet() {
		when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);

		store.remove(42L, "stomp-session-1");

		verify(zSetOperations).remove(
			"bidlive:media:{42}:viewer-sessions",
			"stomp-session-1"
		);
	}

	@Test
	void countsSessionsInAuctionSet() {
		when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
		when(zSetOperations.zCard(
			"bidlive:media:{42}:viewer-sessions"
		)).thenReturn(18L);

		assertThat(store.count(42L)).isEqualTo(18L);
	}

	@Test
	void treatsMissingAuctionSetAsEmpty() {
		when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
		when(zSetOperations.zCard(
			"bidlive:media:{42}:viewer-sessions"
		)).thenReturn(null);

		assertThat(store.count(42L)).isZero();
	}
}
