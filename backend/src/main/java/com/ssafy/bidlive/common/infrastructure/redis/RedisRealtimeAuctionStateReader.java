package com.ssafy.bidlive.common.infrastructure.redis;

import static com.ssafy.bidlive.common.application.RealtimeAuctionStateQueryException.Reason.AUCTION_STATE_UNAVAILABLE;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.ssafy.bidlive.common.application.RealtimeAuctionStateQueryException;
import com.ssafy.bidlive.common.application.RealtimeAuctionStateReader;
import com.ssafy.bidlive.common.application.RealtimeAuctionStateSnapshot;
import com.ssafy.bidlive.common.application.RealtimeAuctionStateSnapshot.Status;
import com.ssafy.bidlive.common.support.redis.RealtimeAuctionRedisKey;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/** Redis 상태 Hash와 회원 입찰 Hash를 한 트랜잭션에서 읽어 공개 스냅샷을 복원합니다. */
@Repository
public class RedisRealtimeAuctionStateReader implements RealtimeAuctionStateReader {

	private final StringRedisTemplate redisTemplate;

	public RedisRealtimeAuctionStateReader(StringRedisTemplate redisTemplate) {
		this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate");
	}

	@Override
	public RealtimeAuctionStateSnapshot read(long auctionId) {
		if (auctionId <= 0) {
			throw unavailable();
		}

		try {
			List<Object> results = redisTemplate.execute(
				new StateAndBidParticipantCountCallback(
					RealtimeAuctionRedisKey.auctionStateKey(auctionId),
					RealtimeAuctionRedisKey.auctionMemberBidsKey(auctionId)
				)
			);
			return toSnapshot(results);
		} catch (RealtimeAuctionStateQueryException exception) {
			throw exception;
		} catch (RuntimeException exception) {
			throw new RealtimeAuctionStateQueryException(
				AUCTION_STATE_UNAVAILABLE,
				exception
			);
		}
	}

	private static RealtimeAuctionStateSnapshot toSnapshot(List<Object> results) {
		if (results == null || results.size() < 2) {
			throw unavailable();
		}
		if (!(results.get(0) instanceof Map<?, ?> rawState) || rawState.isEmpty()) {
			throw unavailable();
		}
		if (!(results.get(1) instanceof Number bidParticipantCount)) {
			throw unavailable();
		}

		try {
			return new RealtimeAuctionStateSnapshot(
				parseLong(rawState, "version"),
				Status.valueOf(requireText(rawState, "status")),
				Instant.ofEpochMilli(parseLong(rawState, "endAt")),
				Math.toIntExact(parseLong(rawState, "extensionCount")),
				parseLong(rawState, "bidCount"),
				bidParticipantCount.longValue()
			);
		} catch (IllegalArgumentException | ArithmeticException exception) {
			throw new RealtimeAuctionStateQueryException(
				AUCTION_STATE_UNAVAILABLE,
				exception
			);
		}
	}

	private static long parseLong(Map<?, ?> state, String fieldName) {
		return Long.parseLong(requireText(state, fieldName));
	}

	private static String requireText(Map<?, ?> state, String fieldName) {
		Object value = state.get(fieldName);
		if (!(value instanceof String text) || text.isBlank()) {
			throw unavailable();
		}
		return text;
	}

	private static RealtimeAuctionStateQueryException unavailable() {
		return new RealtimeAuctionStateQueryException(AUCTION_STATE_UNAVAILABLE);
	}

	/** 상태 Hash와 고유 입찰자 수를 다른 쓰기 사이에 끼지 않도록 MULTI/EXEC로 조회합니다. */
	private record StateAndBidParticipantCountCallback(
		String stateKey,
		String memberBidsKey
	) implements SessionCallback<List<Object>> {

		@Override
		@SuppressWarnings({"rawtypes", "unchecked"})
		public <K, V> List<Object> execute(RedisOperations<K, V> operations)
			throws DataAccessException {
			RedisOperations redisOperations = operations;
			redisOperations.multi();
			redisOperations.opsForHash().entries(stateKey);
			redisOperations.opsForHash().size(memberBidsKey);
			return redisOperations.exec();
		}
	}
}
