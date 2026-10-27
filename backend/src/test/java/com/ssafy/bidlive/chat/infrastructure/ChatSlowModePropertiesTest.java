package com.ssafy.bidlive.chat.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

/** 슬로우 모드 기본 간격과 설정값의 양수 제약을 검증한다. */
class ChatSlowModePropertiesTest {

	@Test
	void providesThreeSecondDefaultInterval() {
		ChatSlowModeProperties properties = new ChatSlowModeProperties();

		assertThat(properties.getInterval())
			.isEqualTo(Duration.ofSeconds(3));
	}

	@Test
	void acceptsPositiveIntervalOverride() {
		ChatSlowModeProperties properties = new ChatSlowModeProperties();

		properties.setInterval(Duration.ofMillis(750));

		assertThat(properties.getInterval())
			.isEqualTo(Duration.ofMillis(750));
	}

	@Test
	void rejectsNullSubMillisecondZeroAndNegativeInterval() {
		ChatSlowModeProperties properties = new ChatSlowModeProperties();

		assertThatThrownBy(() -> properties.setInterval(null))
			.isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() ->
			properties.setInterval(Duration.ofNanos(999_999))
		).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> properties.setInterval(Duration.ZERO))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() ->
			properties.setInterval(Duration.ofMillis(-1))
		).isInstanceOf(IllegalArgumentException.class);
	}
}
