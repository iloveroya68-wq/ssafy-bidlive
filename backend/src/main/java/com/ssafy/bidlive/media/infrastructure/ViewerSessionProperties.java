package com.ssafy.bidlive.media.infrastructure;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 활성 STOMP 세션의 TTL과 정리 실행 간격 설정을 제공합니다. */
@ConfigurationProperties(prefix = "viewer")
public class ViewerSessionProperties {

	static final long DEFAULT_TTL_SECONDS = 30L;
	static final long DEFAULT_CLEANUP_INTERVAL_SECONDS = 10L;

	private long ttlSeconds = DEFAULT_TTL_SECONDS;
	private long cleanupIntervalSeconds = DEFAULT_CLEANUP_INTERVAL_SECONDS;

	public long getTtlSeconds() {
		return ttlSeconds;
	}

	public void setTtlSeconds(long ttlSeconds) {
		this.ttlSeconds = requirePositive(ttlSeconds, "ttlSeconds");
	}

	public long getCleanupIntervalSeconds() {
		return cleanupIntervalSeconds;
	}

	public void setCleanupIntervalSeconds(long cleanupIntervalSeconds) {
		this.cleanupIntervalSeconds = requirePositive(
			cleanupIntervalSeconds,
			"cleanupIntervalSeconds"
		);
	}

	public Duration viewerTtl() {
		return Duration.ofSeconds(ttlSeconds);
	}

	public Duration redisKeyTtl() {
		return Duration.ofSeconds(Math.addExact(
			ttlSeconds,
			cleanupIntervalSeconds
		));
	}

	private static long requirePositive(long value, String name) {
		if (value <= 0) {
			throw new IllegalArgumentException(name + "는 양수여야 합니다.");
		}
		return value;
	}
}
