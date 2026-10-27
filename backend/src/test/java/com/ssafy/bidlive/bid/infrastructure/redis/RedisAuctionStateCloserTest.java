package com.ssafy.bidlive.bid.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import com.ssafy.bidlive.auction.domain.AuctionCanceledEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StreamUtils;

/**
 * 강제 종료가 실시간 입력을 실제로 막는지 검증한다. 스크립트가 읽는 Key·필드와 한 글자라도
 * 어긋나면 종료된 방에 입찰이 계속 접수되고 그 홀드는 아무도 풀지 않는다(S15P11A704-537).
 *
 * <p>{@link RedisAuctionStateCloserIntegrationTest}가 같은 사실을 실제 Lua로 확인하지만 그쪽은
 * `redis-server`가 있어야 돌고 <b>이 레포의 CI에는 테스트 잡이 없다</b> — 그래서 게이트 대조는
 * 여기 원문 비교로도 남겨 둔다. <b>지금 실제로 도는 유일한 방어선이다.</b> 겹치는 편이 낫다.
 */
class RedisAuctionStateCloserTest {

	private static final AuctionCanceledEvent EVENT = new AuctionCanceledEvent(
		100L, "ADMIN_FORCE_END", Instant.parse("2026-07-31T11:02:13Z")
	);

	private StringRedisTemplate redisTemplate;
	private HashOperations<String, Object, Object> hashOperations;
	private RedisAuctionStateCloser closer;

	@SuppressWarnings("unchecked")
	@BeforeEach
	void setUp() {
		redisTemplate = mock(StringRedisTemplate.class);
		hashOperations = mock(HashOperations.class);
		when(redisTemplate.opsForHash()).thenReturn(hashOperations);
		closer = new RedisAuctionStateCloser(redisTemplate);
	}

	@Test
	void closesTheStatusFieldTheBidScriptGatesOn() {
		closer.onAuctionCanceled(EVENT);

		verify(hashOperations).put("bid:{bid}:auction:100:state", "status", "ENDED");
	}

	/** `endAt`은 **예정** 마감이라 실시간 화면·집계가 그대로 읽어야 한다 — 종료가 덮어쓰지 않는다 */
	@Test
	void touchesNothingButStatus() {
		closer.onAuctionCanceled(EVENT);

		verify(hashOperations).put("bid:{bid}:auction:100:state", "status", "ENDED");
		verifyNoMoreInteractions(hashOperations);
	}

	/**
	 * 쓰는 값이 게이트를 닫는다는 근거. 게이트가 `status` 말고 다른 걸 보게 바뀌면 여기서 깨진다 —
	 * 실 Redis 통합 테스트는 바이너리 없이 안 도니 이 대조를 지우면 잡아 줄 것이 남지 않는다.
	 */
	@Test
	void writesAValueBothLuaGatesReject() throws Exception {
		assertThat(script("bid-process.lua"))
			.as("입찰 게이트가 status를 'LIVE'와 비교하지 않으면 'ENDED'로는 못 막는다")
			.contains("local auctionStatus = redis.call('HGET', KEYS[1], 'status')")
			.contains("if auctionStatus ~= 'LIVE' then");
		assertThat(script("prediction-participation-process.lua"))
			.contains("local auctionStatus = redis.call('HGET', KEYS[1], 'status')")
			.contains("if auctionStatus ~= 'LIVE' then");
	}

	private static String script(String name) throws Exception {
		try (var input = new ClassPathResource("redis/lua/" + name).getInputStream()) {
			return StreamUtils.copyToString(input, StandardCharsets.UTF_8);
		}
	}
}
