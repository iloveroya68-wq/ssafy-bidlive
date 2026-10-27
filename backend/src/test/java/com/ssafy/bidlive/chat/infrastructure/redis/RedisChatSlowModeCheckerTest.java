package com.ssafy.bidlive.chat.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import com.ssafy.bidlive.chat.application.ChatSlowModeResult;
import com.ssafy.bidlive.chat.infrastructure.ChatSlowModeProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

/** Redis Lua 결과를 슬로우 모드 허용 여부와 남은 초로 변환하는지 검증한다. */
@ExtendWith(MockitoExtension.class)
class RedisChatSlowModeCheckerTest {

	private static final Instant REQUESTED_AT =
		Instant.parse("2026-07-31T04:00:00Z");

	@Mock
	private StringRedisTemplate redisTemplate;

	private RedisChatSlowModeChecker checker;

	@BeforeEach
	void setUp() {
		ChatSlowModeProperties properties = new ChatSlowModeProperties();
		properties.setInterval(Duration.ofMillis(2_500));
		checker = new RedisChatSlowModeChecker(redisTemplate, properties);
	}

	@Test
	void permitsFirstRequestWithAtomicSetNxPx() {
		when(redisTemplate.execute(
			any(RedisScript.class),
			eq(List.of("bidlive:chat:{100}:slow-mode:25")),
			eq("2500")
		)).thenReturn(-3L);

		ChatSlowModeResult result = checker.checkAndConsume(
			100L,
			25L,
			REQUESTED_AT
		);

		assertThat(result).isEqualTo(ChatSlowModeResult.permit());
		verify(redisTemplate).execute(
			any(RedisScript.class),
			eq(List.of("bidlive:chat:{100}:slow-mode:25")),
			eq("2500")
		);
	}

	@ParameterizedTest
	@CsvSource({
		"2500, 3",
		"2000, 2",
		"1999, 2",
		"1, 1",
		"0, 1"
	})
	void roundsRemainingMillisecondsUpToRetrySeconds(
		long remainingMillis,
		long expectedSeconds
	) {
		when(redisTemplate.execute(
			any(RedisScript.class),
			any(List.class),
			eq("2500")
		)).thenReturn(remainingMillis);

		ChatSlowModeResult result = checker.checkAndConsume(
			100L,
			25L,
			REQUESTED_AT
		);

		assertThat(result)
			.isEqualTo(ChatSlowModeResult.limited(expectedSeconds));
	}

	@Test
	void rejectsMissingOrMalformedRedisResult() {
		when(redisTemplate.execute(
			any(RedisScript.class),
			any(List.class),
			eq("2500")
		)).thenReturn(null, -1L, -2L, -4L);

		assertInvalidRedisResult();
		assertInvalidRedisResult();
		assertInvalidRedisResult();
		assertInvalidRedisResult();
	}

	@Test
	void rejectsInvalidInputBeforeRedisCall() {
		assertThatThrownBy(() ->
			checker.checkAndConsume(0L, 25L, REQUESTED_AT)
		).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() ->
			checker.checkAndConsume(100L, 0L, REQUESTED_AT)
		).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() ->
			checker.checkAndConsume(100L, 25L, null)
		).isInstanceOf(NullPointerException.class);

		verifyNoInteractions(redisTemplate);
	}

	private void assertInvalidRedisResult() {
		assertThatThrownBy(() ->
			checker.checkAndConsume(100L, 25L, REQUESTED_AT)
		)
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("invalid chat slow mode Redis result");
	}
}
