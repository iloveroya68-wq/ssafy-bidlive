package com.ssafy.bidlive.auction.infrastructure.redis;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.ssafy.bidlive.auction.application.AuctionTimeSyncState;
import com.ssafy.bidlive.auction.application.AuctionTimeSyncStateReader;
import com.ssafy.bidlive.auction.domain.AuctionStatus;
import com.ssafy.bidlive.common.support.redis.RealtimeAuctionRedisKey;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/** Redis Hash의 상태와 마감 시각을 한 번에 읽어 TIME_SYNC 판단 상태로 변환한다. */
@Repository
public class RedisAuctionTimeSyncStateReader
	implements AuctionTimeSyncStateReader {

	private static final List<Object> STATE_FIELDS = List.of(
		"status",
		"endAt"
	);

	private final StringRedisTemplate redisTemplate;

	public RedisAuctionTimeSyncStateReader(
		StringRedisTemplate redisTemplate
	) {
		this.redisTemplate = Objects.requireNonNull(
			redisTemplate,
			"redisTemplate"
		);
	}

	@Override
	public Optional<AuctionTimeSyncState> findByAuctionId(long auctionId) {
		List<Object> values = redisTemplate.opsForHash().multiGet(
			RealtimeAuctionRedisKey.auctionStateKey(auctionId),
			STATE_FIELDS
		);
		if (values == null || values.size() != STATE_FIELDS.size()) {
			return Optional.empty();
		}

		String status = stringValue(values.get(0));
		String endAt = stringValue(values.get(1));
		if (status == null || endAt == null) {
			return Optional.empty();
		}

		try {
			return Optional.of(new AuctionTimeSyncState(
				AuctionStatus.valueOf(status),
				Instant.ofEpochMilli(Long.parseLong(endAt))
			));
		} catch (IllegalArgumentException exception) {
			throw new IllegalStateException(
				"Redis returned an invalid auction time-sync state: auctionId="
					+ auctionId,
				exception
			);
		}
	}

	private static String stringValue(Object value) {
		if (value == null) {
			return null;
		}
		String text = value.toString();
		return text.isBlank() ? null : text;
	}
}
