package com.ssafy.bidlive.media.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

/** 접속 세션 TTL과 정리 간격의 기본값 및 양수 제약을 검증합니다. */
class ViewerSessionPropertiesTest {

	@Test
	void providesHeartbeatAwareDefaults() {
		ViewerSessionProperties properties = new ViewerSessionProperties();

		assertThat(properties.viewerTtl()).isEqualTo(Duration.ofSeconds(30));
		assertThat(properties.getCleanupIntervalSeconds()).isEqualTo(10L);
		assertThat(properties.redisKeyTtl()).isEqualTo(Duration.ofSeconds(40));
	}

	@Test
	void acceptsPositiveOverrides() {
		ViewerSessionProperties properties = new ViewerSessionProperties();

		properties.setTtlSeconds(60L);
		properties.setCleanupIntervalSeconds(20L);

		assertThat(properties.viewerTtl()).isEqualTo(Duration.ofSeconds(60));
		assertThat(properties.getCleanupIntervalSeconds()).isEqualTo(20L);
		assertThat(properties.redisKeyTtl()).isEqualTo(Duration.ofSeconds(80));
	}

	@Test
	void rejectsNonPositiveValues() {
		ViewerSessionProperties properties = new ViewerSessionProperties();

		assertThatThrownBy(() -> properties.setTtlSeconds(0L))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("ttlSeconds는 양수여야 합니다.");
		assertThatThrownBy(() -> properties.setCleanupIntervalSeconds(-1L))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("cleanupIntervalSeconds는 양수여야 합니다.");
	}
}
