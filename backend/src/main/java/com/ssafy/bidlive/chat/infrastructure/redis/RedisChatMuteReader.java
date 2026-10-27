package com.ssafy.bidlive.chat.infrastructure.redis;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import com.ssafy.bidlive.chat.application.ChatMuteReader;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Redis mute Key의 남은 TTL을 채팅 제한 만료 시각으로 변환한다. */
public class RedisChatMuteReader implements ChatMuteReader {

	private static final long MISSING_KEY_TTL = -2L;

	private final StringRedisTemplate redisTemplate;
	private final Clock clock;

	public RedisChatMuteReader(StringRedisTemplate redisTemplate) {
		this(redisTemplate, Clock.systemUTC());
	}

	RedisChatMuteReader(
		StringRedisTemplate redisTemplate,
		Clock clock
	) {
		this.redisTemplate = Objects.requireNonNull(
			redisTemplate,
			"redisTemplate"
		);
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	@Override
	public Optional<Instant> findMutedUntil(
		long auctionId,
		long memberId
	) {
		String key = ChatRedisKey.mute(auctionId, memberId);
		Long remainingMillis = redisTemplate.getExpire(
			key,
			TimeUnit.MILLISECONDS
		);
		if (remainingMillis == null) {
			throw invalidTtl("null");
		}
		if (remainingMillis == MISSING_KEY_TTL || remainingMillis == 0L) {
			return Optional.empty();
		}
		if (remainingMillis < 0L) {
			throw invalidTtl(Long.toString(remainingMillis));
		}
		return Optional.of(clock.instant().plusMillis(remainingMillis));
	}

	private static IllegalStateException invalidTtl(String rawTtl) {
		return new IllegalStateException(
			"invalid chat mute Redis TTL: " + rawTtl
		);
	}
}
