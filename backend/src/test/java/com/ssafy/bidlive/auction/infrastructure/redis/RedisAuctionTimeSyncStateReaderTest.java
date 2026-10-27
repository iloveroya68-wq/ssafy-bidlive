package com.ssafy.bidlive.auction.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import com.ssafy.bidlive.auction.application.AuctionTimeSyncState;
import com.ssafy.bidlive.auction.domain.AuctionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Redis의 TIME_SYNC 상태 조회가 status와 endAt을 한 번에 읽는지 검증한다. */
class RedisAuctionTimeSyncStateReaderTest {

	private StringRedisTemplate redisTemplate;
	private HashOperations<String, Object, Object> hashOperations;
	private RedisAuctionTimeSyncStateReader reader;

	@SuppressWarnings("unchecked")
	@BeforeEach
	void setUp() {
		redisTemplate = mock(StringRedisTemplate.class);
		hashOperations = mock(HashOperations.class);
		when(redisTemplate.opsForHash()).thenReturn(hashOperations);
		reader = new RedisAuctionTimeSyncStateReader(redisTemplate);
	}

	@Test
	void readsStatusAndEndAtFromTheSameHashOperation() {
		Instant endAt = Instant.parse("2026-08-01T12:30:00Z");
		when(hashOperations.multiGet(
			eq("bid:{bid}:auction:100:state"),
			eq(List.of("status", "endAt"))
		)).thenReturn(List.<Object>of(
			"LIVE",
			Long.toString(endAt.toEpochMilli())
		));

		AuctionTimeSyncState state = reader.findByAuctionId(100L).orElseThrow();

		assertThat(state.status()).isEqualTo(AuctionStatus.LIVE);
		assertThat(state.endAt()).isEqualTo(endAt);
		verify(hashOperations).multiGet(
			"bid:{bid}:auction:100:state",
			List.of("status", "endAt")
		);
	}

	@Test
	void returnsEmptyWhenRealtimeStateIsMissingOrIncomplete() {
		when(hashOperations.multiGet(
			eq("bid:{bid}:auction:100:state"),
			eq(List.of("status", "endAt"))
		)).thenReturn(null);
		when(hashOperations.multiGet(
			eq("bid:{bid}:auction:101:state"),
			eq(List.of("status", "endAt"))
		)).thenReturn(Arrays.<Object>asList("LIVE", null));

		assertThat(reader.findByAuctionId(100L)).isEmpty();
		assertThat(reader.findByAuctionId(101L)).isEmpty();
	}

	@Test
	void rejectsMalformedRealtimeState() {
		when(hashOperations.multiGet(
			eq("bid:{bid}:auction:100:state"),
			eq(List.of("status", "endAt"))
		)).thenReturn(List.<Object>of("LIVE", "not-an-epoch"));

		assertThatThrownBy(() -> reader.findByAuctionId(100L))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("auctionId=100");
	}
}
