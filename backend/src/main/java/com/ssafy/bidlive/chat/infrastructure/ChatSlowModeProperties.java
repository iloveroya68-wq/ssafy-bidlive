package com.ssafy.bidlive.chat.infrastructure;

import java.time.Duration;
import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 채팅 전송 사이에 적용할 슬로우 모드 제한 간격 설정을 제공한다. */
@ConfigurationProperties(prefix = "app.chat.slow-mode")
public class ChatSlowModeProperties {

	static final Duration DEFAULT_INTERVAL = Duration.ofSeconds(3);

	private Duration interval = DEFAULT_INTERVAL;

	public Duration getInterval() {
		return interval;
	}

	public void setInterval(Duration interval) {
		this.interval = requirePositive(interval);
	}

	private static Duration requirePositive(Duration interval) {
		Objects.requireNonNull(interval, "interval");
		if (interval.compareTo(Duration.ofMillis(1)) < 0) {
			throw new IllegalArgumentException(
				"slow mode interval must be at least one millisecond"
			);
		}
		return interval;
	}
}
