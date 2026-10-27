package com.ssafy.bidlive.media.infrastructure.redis;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

import com.ssafy.bidlive.media.application.port.out.ViewerSessionStore;
import com.ssafy.bidlive.media.infrastructure.ViewerSessionProperties;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

/** 활성 STOMP 세션을 TTL이 있는 Redis ZSET으로 저장하고 만료 정리·활성 수 집계를 수행합니다. */
@Repository
public class RedisViewerSessionStore implements ViewerSessionStore {

	private static final RedisScript<Long> RECORD_SESSION_SCRIPT = RedisScript.of("""
		redis.call('ZADD', KEYS[1], ARGV[1], ARGV[2])
		redis.call('PEXPIRE', KEYS[1], ARGV[3])
		return 1
		""", Long.class);

	private final StringRedisTemplate redisTemplate;
	private final Duration redisKeyTtl;

	public RedisViewerSessionStore(
		StringRedisTemplate redisTemplate,
		ViewerSessionProperties properties
	) {
		this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate");
		this.redisKeyTtl = Objects.requireNonNull(
			properties,
			"properties"
		).redisKeyTtl();
	}

	@Override
	public void record(
		long auctionId,
		String stompSessionId,
		Instant lastSeenAt
	) {
		Objects.requireNonNull(stompSessionId, "stompSessionId");
		Objects.requireNonNull(lastSeenAt, "lastSeenAt");

		Long result = redisTemplate.execute(
			RECORD_SESSION_SCRIPT,
			List.of(MediaRedisKey.viewerSessions(auctionId)),
			Long.toString(lastSeenAt.toEpochMilli()),
			stompSessionId,
			Long.toString(redisKeyTtl.toMillis())
		);
		if (result == null) {
			throw new IllegalStateException("접속 세션 기록 결과를 확인할 수 없습니다.");
		}
	}

	@Override
	public void remove(long auctionId, String stompSessionId) {
		Objects.requireNonNull(stompSessionId, "stompSessionId");

		redisTemplate.opsForZSet().remove(
			MediaRedisKey.viewerSessions(auctionId),
			stompSessionId
		);
	}

	@Override
	public long count(long auctionId) {
		Long count = redisTemplate.opsForZSet().zCard(
			MediaRedisKey.viewerSessions(auctionId)
		);
		return count == null ? 0L : count;
	}

	@Override
	public long removeExpiredAndCount(long auctionId, Instant expirationCutoff) {
		Objects.requireNonNull(expirationCutoff, "expirationCutoff");

		String key = MediaRedisKey.viewerSessions(auctionId);
		List<Object> results = redisTemplate.execute(
			new RemoveExpiredAndCountCallback(key, expirationCutoff.toEpochMilli())
		);

		if (results == null || results.size() < 2 || !(results.get(1) instanceof Number count)) {
			throw new IllegalStateException("접속 세션 정리 결과를 확인할 수 없습니다.");
		}

		return count.longValue();
	}

	/** 만료 세션 제거와 남은 세션 수 조회를 하나의 Redis 트랜잭션으로 실행합니다. */
	private record RemoveExpiredAndCountCallback(
		String key,
		long expiredAtEpochMillis
	) implements SessionCallback<List<Object>> {

		@Override
		@SuppressWarnings({"rawtypes", "unchecked"})
		public <K, V> List<Object> execute(RedisOperations<K, V> operations)
			throws DataAccessException {
			RedisOperations redisOperations = operations;

			redisOperations.multi();
			redisOperations.opsForZSet().removeRangeByScore(
				key,
				Double.NEGATIVE_INFINITY,
				expiredAtEpochMillis
			);
			redisOperations.opsForZSet().size(key);
			return redisOperations.exec();
		}
	}
}
