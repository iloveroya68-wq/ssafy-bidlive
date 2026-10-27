package com.ssafy.bidlive.chat.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 채팅 ULID의 형식·유일성과 시각 역행 시 단조 증가를 검증한다. */
class MonotonicUlidMessageIdGeneratorTest {

	private Clock clock;
	private SecureRandom secureRandom;

	@BeforeEach
	void setUp() {
		clock = mock(Clock.class);
		secureRandom = mock(SecureRandom.class);
		doAnswer(invocation -> {
			byte[] bytes = invocation.getArgument(0);
			java.util.Arrays.fill(bytes, (byte) 0);
			return null;
		}).when(secureRandom).nextBytes(any(byte[].class));
	}

	@Test
	void createsCanonicalTwentySixCharacterUlid() {
		when(clock.millis()).thenReturn(1_722_393_600_000L);
		MonotonicUlidMessageIdGenerator generator = generator();

		String messageId = generator.nextId().value();

		assertThat(messageId)
			.hasSize(26)
			.matches("[0-7][0-9A-HJKMNP-TV-Z]{25}");
	}

	@Test
	void increasesWithinSameMillisecondWithoutDuplicate() {
		when(clock.millis()).thenReturn(1_722_393_600_000L);
		MonotonicUlidMessageIdGenerator generator = generator();

		String previous = generator.nextId().value();
		Set<String> issuedIds = new HashSet<>();
		issuedIds.add(previous);

		for (int index = 0; index < 1_000; index++) {
			String current = generator.nextId().value();
			assertThat(current).isGreaterThan(previous);
			issuedIds.add(current);
			previous = current;
		}

		assertThat(issuedIds).hasSize(1_001);
	}

	@Test
	void keepsIncreasingWhenClockMovesBackward() {
		when(clock.millis())
			.thenReturn(2_000L)
			.thenReturn(1_000L);
		MonotonicUlidMessageIdGenerator generator = generator();

		String first = generator.nextId().value();
		String second = generator.nextId().value();

		assertThat(second).isGreaterThan(first);
	}

	@Test
	void timestampPrefixIncreasesWithClock() {
		when(clock.millis())
			.thenReturn(1_000L)
			.thenReturn(2_000L);
		MonotonicUlidMessageIdGenerator generator = generator();

		String first = generator.nextId().value();
		String second = generator.nextId().value();

		assertThat(second).isGreaterThan(first);
		assertThat(second.substring(0, 10))
			.isGreaterThan(first.substring(0, 10));
	}

	private MonotonicUlidMessageIdGenerator generator() {
		return new MonotonicUlidMessageIdGenerator(clock, secureRandom);
	}
}
