package com.ssafy.bidlive.chat.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Redis TTL 조회 결과를 mute 만료 시각과 실패로 구분하는지 검증한다. */
@ExtendWith(MockitoExtension.class)
class RedisChatMuteReaderTest {

	private static final Instant NOW =
		Instant.parse("2026-07-31T04:00:00Z");
	private static final String MUTE_KEY =
		"bidlive:chat:{100}:mute:25";

	@Mock
	private StringRedisTemplate redisTemplate;

	private RedisChatMuteReader reader;

	@BeforeEach
	void setUp() {
		reader = new RedisChatMuteReader(
			redisTemplate,
			Clock.fixed(NOW, ZoneOffset.UTC)
		);
	}

	@Test
	void convertsPositiveTtlToMutedUntil() {
		when(redisTemplate.getExpire(
			MUTE_KEY,
			TimeUnit.MILLISECONDS
		)).thenReturn(2_500L);

		Optional<Instant> mutedUntil =
			reader.findMutedUntil(100L, 25L);

		assertThat(mutedUntil)
			.contains(NOW.plusMillis(2_500L));
		verify(redisTemplate).getExpire(
			MUTE_KEY,
			TimeUnit.MILLISECONDS
		);
	}

	@Test
	void returnsEmptyForMissingOrExpiringKey() {
		when(redisTemplate.getExpire(
			MUTE_KEY,
			TimeUnit.MILLISECONDS
		)).thenReturn(-2L, 0L);

		assertThat(reader.findMutedUntil(100L, 25L)).isEmpty();
		assertThat(reader.findMutedUntil(100L, 25L)).isEmpty();
	}

	@Test
	void rejectsMissingExpiryAndMalformedTtl() {
		when(redisTemplate.getExpire(
			MUTE_KEY,
			TimeUnit.MILLISECONDS
		)).thenReturn(null, -1L, -3L);

		assertInvalidTtl("null");
		assertInvalidTtl("-1");
		assertInvalidTtl("-3");
	}

	@Test
	void rejectsInvalidIdentifiersBeforeRedisCall() {
		assertThatThrownBy(() -> reader.findMutedUntil(0L, 25L))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> reader.findMutedUntil(100L, 0L))
			.isInstanceOf(IllegalArgumentException.class);

		verifyNoInteractions(redisTemplate);
	}

	@Test
	void propagatesRedisFailureWithoutTreatingMemberAsUnmuted() {
		DataAccessResourceFailureException failure =
			new DataAccessResourceFailureException("Redis unavailable");
		when(redisTemplate.getExpire(
			MUTE_KEY,
			TimeUnit.MILLISECONDS
		)).thenThrow(failure);

		assertThatThrownBy(() -> reader.findMutedUntil(100L, 25L))
			.isSameAs(failure);
	}

	private void assertInvalidTtl(String rawTtl) {
		assertThatThrownBy(() -> reader.findMutedUntil(100L, 25L))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("invalid chat mute Redis TTL: " + rawTtl);
	}
}
