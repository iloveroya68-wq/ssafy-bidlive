package com.ssafy.bidlive.bid.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Map;

import com.ssafy.bidlive.auction.domain.AuctionStartedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 방송 시작 시 만들어지는 실시간 경매 상태 Hash가 입찰과 연장 판정 필드를 모두 갖는지 검증한다.
 * 하나라도 빠지면 시작된 방인데도 실시간 정책이 잘못 판정된다.
 */
class RedisAuctionStateInitializerTest {

	private static final Instant STARTED_AT = Instant.parse("2026-07-21T11:02:13Z");
	private static final Instant END_AT = Instant.parse("2026-07-21T11:17:13Z");

	private StringRedisTemplate redisTemplate;
	private HashOperations<String, Object, Object> hashOperations;
	private RedisAuctionStateInitializer initializer;

	@SuppressWarnings("unchecked")
	@BeforeEach
	void setUp() {
		redisTemplate = mock(StringRedisTemplate.class);
		hashOperations = mock(HashOperations.class);
		when(redisTemplate.opsForHash()).thenReturn(hashOperations);
		initializer = new RedisAuctionStateInitializer(redisTemplate);
	}

	@Test
	void writesEveryFieldRealtimePoliciesRead() {
		initializer.onAuctionStarted(new AuctionStartedEvent(
			100L, 25L, 50_000L, STARTED_AT, END_AT
		));

		@SuppressWarnings("unchecked")
		ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
		verify(hashOperations).putAll(
			eq("bid:{bid}:auction:100:state"),
			captor.capture()
		);
		Map<String, String> state = captor.getValue();
		assertThat(state)
			.containsEntry("status", "LIVE")
			.containsEntry("reservePrice", "50000")
			.containsEntry("sellerId", "25")
			.containsEntry("bidCount", "0")
			.containsEntry("participantCount", "0")
			.containsEntry("extensionCount", "0")
			.containsEntry("version", "0")
			.containsEntry("updatedAt", "2026-07-21T11:02:13Z");
	}

	/** 스크립트가 `tonumber`로 읽어 요청 시각(epoch millis)과 비교하므로 ISO 문자열이면 안 된다 */
	@Test
	void writesEndAtAsEpochMillis() {
		initializer.onAuctionStarted(new AuctionStartedEvent(
			100L, 25L, 50_000L, STARTED_AT, END_AT
		));

		@SuppressWarnings("unchecked")
		ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
		verify(hashOperations).putAll(eq("bid:{bid}:auction:100:state"), captor.capture());

		assertThat(captor.getValue().get("endAt"))
			.isEqualTo(String.valueOf(END_AT.toEpochMilli()));
	}
}
