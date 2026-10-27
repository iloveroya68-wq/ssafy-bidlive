package com.ssafy.bidlive.common.infrastructure.redis;

import static com.ssafy.bidlive.common.application.RealtimeAuctionStateQueryException.Reason.AUCTION_STATE_UNAVAILABLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.ssafy.bidlive.common.application.RealtimeAuctionStateQueryException;
import com.ssafy.bidlive.common.application.RealtimeAuctionStateSnapshot.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Redis 공개 상태의 정상 복원과 불완전 스냅샷 거부를 검증합니다. */
@ExtendWith(MockitoExtension.class)
class RedisRealtimeAuctionStateReaderTest {

	@Mock
	private StringRedisTemplate redisTemplate;

	private RedisRealtimeAuctionStateReader reader;

	@BeforeEach
	void setUp() {
		reader = new RedisRealtimeAuctionStateReader(redisTemplate);
	}

	@Test
	@SuppressWarnings("unchecked")
	void readsStateAndUniqueBidParticipantCountFromOneRedisTransaction() {
		when(redisTemplate.execute(any(SessionCallback.class))).thenReturn(List.of(
			Map.of(
				"version", "184",
				"status", "LIVE",
				"endAt", "1785672900000",
				"extensionCount", "1",
				"bidCount", "7",
				"participantCount", "9"
			),
			5L
		));

		var snapshot = reader.read(42L);

		assertThat(snapshot.stateVersion()).isEqualTo(184L);
		assertThat(snapshot.status()).isEqualTo(Status.LIVE);
		assertThat(snapshot.endAt()).isEqualTo(Instant.ofEpochMilli(1785672900000L));
		assertThat(snapshot.extendCount()).isEqualTo(1);
		assertThat(snapshot.bidCount()).isEqualTo(7L);
		assertThat(snapshot.bidParticipantCount()).isEqualTo(5L);
		verify(redisTemplate).execute(any(SessionCallback.class));
	}

	@Test
	@SuppressWarnings("unchecked")
	void rejectsMissingStateWithoutCreatingVersionZeroSnapshot() {
		when(redisTemplate.execute(any(SessionCallback.class))).thenReturn(List.of(
			Map.of(),
			0L
		));

		assertUnavailable(() -> reader.read(42L));
	}

	@Test
	@SuppressWarnings("unchecked")
	void rejectsIncompleteState() {
		when(redisTemplate.execute(any(SessionCallback.class))).thenReturn(List.of(
			Map.of(
				"version", "184",
				"status", "LIVE",
				"endAt", "1785672900000",
				"extensionCount", "1"
			),
			5L
		));

		assertUnavailable(() -> reader.read(42L));
	}

	@Test
	@SuppressWarnings("unchecked")
	void rejectsNegativeOrMalformedState() {
		when(redisTemplate.execute(any(SessionCallback.class))).thenReturn(List.of(
			Map.of(
				"version", "-1",
				"status", "LIVE",
				"endAt", "not-a-number",
				"extensionCount", "1",
				"bidCount", "7"
			),
			5L
		));

		assertUnavailable(() -> reader.read(42L));
	}

	@Test
	@SuppressWarnings("unchecked")
	void mapsRedisFailureToUnavailable() {
		when(redisTemplate.execute(any(SessionCallback.class)))
			.thenThrow(new IllegalStateException("redis unavailable"));

		assertUnavailable(() -> reader.read(42L));
	}

	private static void assertUnavailable(Runnable invocation) {
		assertThatThrownBy(invocation::run)
			.isInstanceOfSatisfying(
				RealtimeAuctionStateQueryException.class,
				exception -> assertThat(exception.reason())
					.isEqualTo(AUCTION_STATE_UNAVAILABLE)
			);
	}
}
