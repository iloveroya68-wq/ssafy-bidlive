package com.ssafy.bidlive.chat.infrastructure.redis;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import com.ssafy.bidlive.chat.application.ChatSlowModeChecker;
import com.ssafy.bidlive.chat.application.ChatSlowModeResult;
import com.ssafy.bidlive.chat.infrastructure.ChatSlowModeProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

/** Redis TTL로 경매·회원별 채팅 전송 간격을 원자적으로 검사하고 소비한다. */
public class RedisChatSlowModeChecker implements ChatSlowModeChecker {

	private static final long PERMITTED_RESULT = -3L;
	private static final RedisScript<Long> CHECK_AND_CONSUME_SCRIPT =
		RedisScript.of("""
			local created = redis.call(
				'SET',
				KEYS[1],
				'1',
				'NX',
				'PX',
				ARGV[1]
			)
			if created then
				return -3
			end
			return redis.call('PTTL', KEYS[1])
			""", Long.class);

	private final StringRedisTemplate redisTemplate;
	private final long intervalMillis;

	public RedisChatSlowModeChecker(
		StringRedisTemplate redisTemplate,
		ChatSlowModeProperties properties
	) {
		this.redisTemplate = Objects.requireNonNull(
			redisTemplate,
			"redisTemplate"
		);
		this.intervalMillis = Objects.requireNonNull(
			properties,
			"properties"
		).getInterval().toMillis();
	}

	@Override
	public ChatSlowModeResult checkAndConsume(
		long auctionId,
		long memberId,
		Instant requestedAt
	) {
		Objects.requireNonNull(requestedAt, "requestedAt");
		String key = ChatRedisKey.slowMode(auctionId, memberId);
		Long rawResult = redisTemplate.execute(
			CHECK_AND_CONSUME_SCRIPT,
			List.of(key),
			Long.toString(intervalMillis)
		);
		if (rawResult == null) {
			throw invalidResult("null");
		}
		if (rawResult == PERMITTED_RESULT) {
			return ChatSlowModeResult.permit();
		}
		if (rawResult >= 0) {
			return ChatSlowModeResult.limited(
				toRetryAfterSeconds(rawResult)
			);
		}
		throw invalidResult(Long.toString(rawResult));
	}

	private static long toRetryAfterSeconds(long remainingMillis) {
		if (remainingMillis == 0) {
			return 1L;
		}
		long seconds = Math.floorDiv(remainingMillis, 1_000L);
		return remainingMillis % 1_000L == 0
			? seconds
			: Math.addExact(seconds, 1L);
	}

	private static IllegalStateException invalidResult(String rawResult) {
		return new IllegalStateException(
			"invalid chat slow mode Redis result: " + rawResult
		);
	}
}
