package com.ssafy.bidlive.auction.infrastructure.redis;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.bidlive.auction.application.CatchUpRoomEventState;
import com.ssafy.bidlive.auction.application.CatchUpSummaryResult;
import com.ssafy.bidlive.auction.application.CatchUpSummaryStore;
import com.ssafy.bidlive.auction.application.CatchUpTranscriptSegment;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/** RoomEvent 집계, STT 문장과 완성 요약을 TTL이 있는 Redis 값으로 저장한다. */
@Repository
public class RedisCatchUpSummaryStore implements CatchUpSummaryStore {

	private static final int MINUTES_PER_EXTENSION = 5;

	private final StringRedisTemplate redisTemplate;
	private final ObjectMapper objectMapper;

	public RedisCatchUpSummaryStore(
		StringRedisTemplate redisTemplate,
		ObjectMapper objectMapper
	) {
		this.redisTemplate = redisTemplate;
		this.objectMapper = objectMapper;
	}

	@Override
	public Optional<CatchUpSummaryResult> findSummary(long auctionId) {
		String value = redisTemplate.opsForValue().get(CatchUpRedisKey.summary(auctionId));
		return value == null
			? Optional.empty()
			: Optional.of(read(value, CatchUpSummaryResult.class));
	}

	@Override
	public void saveSummary(
		long auctionId,
		CatchUpSummaryResult summary,
		long transcriptRevision,
		Duration ttl
	) {
		redisTemplate.opsForValue().set(
			CatchUpRedisKey.summary(auctionId),
			write(summary),
			ttl
		);
		redisTemplate.opsForValue().set(
			CatchUpRedisKey.summaryRevision(auctionId),
			Long.toString(transcriptRevision),
			ttl
		);
	}

	@Override
	public long findTranscriptRevision(long auctionId) {
		return revision(CatchUpRedisKey.transcriptRevision(auctionId));
	}

	@Override
	public long findSummaryRevision(long auctionId) {
		return revision(CatchUpRedisKey.summaryRevision(auctionId));
	}

	@Override
	public boolean claimSummaryRefresh(long auctionId, Duration ttl) {
		Boolean claimed = redisTemplate.opsForValue().setIfAbsent(
			CatchUpRedisKey.summaryRefreshLock(auctionId),
			"1",
			ttl
		);
		return Boolean.TRUE.equals(claimed);
	}

	@Override
	public void releaseSummaryRefresh(long auctionId) {
		redisTemplate.delete(CatchUpRedisKey.summaryRefreshLock(auctionId));
	}

	@Override
	public Optional<CatchUpRoomEventState> findRoomEvents(long auctionId) {
		Map<Object, Object> fields = redisTemplate.opsForHash()
			.entries(CatchUpRedisKey.state(auctionId));
		if (fields.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(new CatchUpRoomEventState(
			longValue(fields, "bidCount", 0),
			longValue(fields, "participantCount", 0),
			intValue(fields, "extensionCount", 0),
			intValue(fields, "extendedMinutes", 0),
			instantValue(fields, "startedAt"),
			instantValue(fields, "endAt")
		));
	}

	@Override
	public List<CatchUpTranscriptSegment> findTranscripts(long auctionId) {
		Map<Object, Object> values = redisTemplate.opsForHash()
			.entries(CatchUpRedisKey.transcripts(auctionId));
		List<CatchUpTranscriptSegment> result = new ArrayList<>(values.size());
		for (Object value : values.values()) {
			result.add(read(String.valueOf(value), CatchUpTranscriptSegment.class));
		}
		result.sort(Comparator.comparingLong(CatchUpTranscriptSegment::sequence));
		return List.copyOf(result);
	}

	@Override
	public boolean hasSource(long auctionId) {
		return hasKey(CatchUpRedisKey.state(auctionId))
			|| hasKey(CatchUpRedisKey.transcripts(auctionId))
			|| hasKey(CatchUpRedisKey.summary(auctionId));
	}

	@Override
	public void recordStarted(
		long auctionId,
		Instant startedAt,
		Instant endAt,
		Duration ttl
	) {
		Map<String, String> values = new HashMap<>();
		values.put("startedAt", epoch(startedAt));
		values.put("endAt", epoch(endAt));
		values.put("bidCount", "0");
		values.put("participantCount", "0");
		values.put("extensionCount", "0");
		values.put("extendedMinutes", "0");
		putState(auctionId, values, ttl);
	}

	@Override
	public void recordBidCounts(
		long auctionId,
		long bidCount,
		long participantCount,
		Duration ttl
	) {
		putState(auctionId, Map.of(
			"bidCount", Long.toString(bidCount),
			"participantCount", Long.toString(participantCount)
		), ttl);
	}

	@Override
	public void recordExtension(
		long auctionId,
		Instant endAt,
		int extensionCount,
		Duration ttl
	) {
		putState(auctionId, Map.of(
			"endAt", epoch(endAt),
			"extensionCount", Integer.toString(extensionCount),
			"extendedMinutes", Integer.toString(extensionCount * MINUTES_PER_EXTENSION)
		), ttl);
	}

	@Override
	public boolean claimAudioChunk(long auctionId, long sequence, Duration ttl) {
		Boolean claimed = redisTemplate.opsForValue().setIfAbsent(
			CatchUpRedisKey.audioClaim(auctionId, sequence),
			"1",
			ttl
		);
		return Boolean.TRUE.equals(claimed);
	}

	@Override
	public void releaseAudioChunk(long auctionId, long sequence) {
		redisTemplate.delete(CatchUpRedisKey.audioClaim(auctionId, sequence));
	}

	@Override
	public void appendTranscript(
		long auctionId,
		CatchUpTranscriptSegment segment,
		Duration ttl
	) {
		String key = CatchUpRedisKey.transcripts(auctionId);
		Boolean added = redisTemplate.opsForHash().putIfAbsent(
			key,
			Long.toString(segment.sequence()),
			write(segment)
		);
		redisTemplate.expire(key, ttl);
		if (Boolean.TRUE.equals(added)) {
			String revisionKey = CatchUpRedisKey.transcriptRevision(auctionId);
			redisTemplate.opsForValue().increment(revisionKey);
			redisTemplate.expire(revisionKey, ttl);
		}
	}

	private void putState(long auctionId, Map<String, String> values, Duration ttl) {
		String key = CatchUpRedisKey.state(auctionId);
		redisTemplate.opsForHash().putAll(key, values);
		redisTemplate.expire(key, ttl);
	}

	private boolean hasKey(String key) {
		return Boolean.TRUE.equals(redisTemplate.hasKey(key));
	}

	private long revision(String key) {
		String value = redisTemplate.opsForValue().get(key);
		return value == null ? 0 : Long.parseLong(value);
	}

	private String write(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("catch-up Redis value serialization failed", exception);
		}
	}

	private <T> T read(String value, Class<T> type) {
		try {
			return objectMapper.readValue(value, type);
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("catch-up Redis value deserialization failed", exception);
		}
	}

	private static String epoch(Instant instant) {
		return instant == null ? "" : Long.toString(instant.toEpochMilli());
	}

	private static long longValue(Map<Object, Object> values, String key, long fallback) {
		Object value = values.get(key);
		return value == null ? fallback : Long.parseLong(String.valueOf(value));
	}

	private static int intValue(Map<Object, Object> values, String key, int fallback) {
		Object value = values.get(key);
		return value == null ? fallback : Integer.parseInt(String.valueOf(value));
	}

	private static Instant instantValue(Map<Object, Object> values, String key) {
		Object value = values.get(key);
		return value == null || String.valueOf(value).isBlank()
			? null
			: Instant.ofEpochMilli(Long.parseLong(String.valueOf(value)));
	}
}
