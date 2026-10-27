package com.ssafy.bidlive.chat.infrastructure.redis;

import java.time.Instant;
import java.util.List;

import com.ssafy.bidlive.chat.application.ChatModerationPenaltyPort;
import com.ssafy.bidlive.chat.application.ModerationPenaltyResult;
import com.ssafy.bidlive.chat.infrastructure.ChatAsyncModerationProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/** Redis Lua로 위반 누적과 임계치 도달 시 뮤트 TTL 설정을 원자 처리합니다. */
@Component
public class RedisChatModerationPenaltyAdapter
	implements ChatModerationPenaltyPort {

	private static final RedisScript<List> RECORD = RedisScript.of("""
		local count = redis.call('INCR', KEYS[1])
		if count == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end
		local muted = 0
		if count >= tonumber(ARGV[2]) then
			redis.call('SET', KEYS[2], '1', 'PX', ARGV[3])
			muted = 1
		end
		return {count, muted}
		""", List.class);

	private final StringRedisTemplate redisTemplate;
	private final ChatAsyncModerationProperties properties;

	public RedisChatModerationPenaltyAdapter(
		StringRedisTemplate redisTemplate,
		ChatAsyncModerationProperties properties
	) {
		this.redisTemplate = redisTemplate;
		this.properties = properties;
	}

	@Override
	public ModerationPenaltyResult recordViolation(
		long auctionId,
		long memberId,
		Instant now
	) {
		List<?> result = redisTemplate.execute(
			RECORD,
			List.of(
				ChatRedisKey.moderationViolations(auctionId, memberId),
				ChatRedisKey.mute(auctionId, memberId)
			),
			Long.toString(properties.getViolationWindow().toMillis()),
			Integer.toString(properties.getViolationThreshold()),
			Long.toString(properties.getMuteDuration().toMillis())
		);
		if (result == null || result.size() != 2) {
			throw new IllegalStateException("invalid moderation penalty result");
		}
		long count = ((Number) result.get(0)).longValue();
		boolean muted = ((Number) result.get(1)).longValue() == 1L;
		return new ModerationPenaltyResult(
			count,
			muted
				? java.util.Optional.of(now.plus(properties.getMuteDuration()))
				: java.util.Optional.empty()
		);
	}
}
