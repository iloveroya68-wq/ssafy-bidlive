package com.ssafy.bidlive.chat.infrastructure;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Arrays;
import java.util.Objects;

import com.ssafy.bidlive.chat.domain.ChatMessageId;
import com.ssafy.bidlive.chat.domain.ChatMessageIdGenerator;
import org.springframework.stereotype.Component;

/** 동일 밀리초와 시계 역행에서도 정렬 순서를 유지하는 채팅 ULID를 발급한다. */
@Component
public class MonotonicUlidMessageIdGenerator implements ChatMessageIdGenerator {

	private static final char[] CROCKFORD_BASE32 =
		"0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
	private static final long MAX_TIMESTAMP = (1L << 48) - 1;
	private static final int RANDOMNESS_LENGTH = 10;

	private final Clock clock;
	private final SecureRandom secureRandom;
	private final byte[] lastRandomness = new byte[RANDOMNESS_LENGTH];

	private long lastTimestamp = -1;

	public MonotonicUlidMessageIdGenerator() {
		this(Clock.systemUTC(), new SecureRandom());
	}

	MonotonicUlidMessageIdGenerator(Clock clock, SecureRandom secureRandom) {
		this.clock = Objects.requireNonNull(clock, "clock");
		this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom");
	}

	@Override
	public synchronized ChatMessageId nextId() {
		long currentTimestamp = clock.millis();
		validateTimestamp(currentTimestamp);

		long effectiveTimestamp = Math.max(currentTimestamp, lastTimestamp);
		if (effectiveTimestamp > lastTimestamp) {
			secureRandom.nextBytes(lastRandomness);
		} else if (!incrementRandomness()) {
			effectiveTimestamp = nextTimestamp(lastTimestamp);
			secureRandom.nextBytes(lastRandomness);
		}

		lastTimestamp = effectiveTimestamp;
		return new ChatMessageId(encode(effectiveTimestamp, lastRandomness));
	}

	private static void validateTimestamp(long timestamp) {
		if (timestamp < 0 || timestamp > MAX_TIMESTAMP) {
			throw new IllegalStateException("clock timestamp is outside ULID range");
		}
	}

	private static long nextTimestamp(long timestamp) {
		if (timestamp >= MAX_TIMESTAMP) {
			throw new IllegalStateException("ULID timestamp range is exhausted");
		}
		return timestamp + 1;
	}

	private boolean incrementRandomness() {
		for (int index = lastRandomness.length - 1; index >= 0; index--) {
			int incremented = (lastRandomness[index] & 0xFF) + 1;
			lastRandomness[index] = (byte) incremented;
			if (incremented <= 0xFF) {
				return true;
			}
		}
		Arrays.fill(lastRandomness, (byte) 0);
		return false;
	}

	private static String encode(long timestamp, byte[] randomness) {
		char[] encoded = new char[26];

		long remainingTimestamp = timestamp;
		for (int index = 9; index >= 0; index--) {
			encoded[index] = CROCKFORD_BASE32[(int) (remainingTimestamp & 31)];
			remainingTimestamp >>>= 5;
		}

		BigInteger remainingRandomness = new BigInteger(1, randomness);
		BigInteger radix = BigInteger.valueOf(32);
		for (int index = 25; index >= 10; index--) {
			BigInteger[] quotientAndRemainder =
				remainingRandomness.divideAndRemainder(radix);
			encoded[index] =
				CROCKFORD_BASE32[quotientAndRemainder[1].intValue()];
			remainingRandomness = quotientAndRemainder[0];
		}

		return new String(encoded);
	}
}
