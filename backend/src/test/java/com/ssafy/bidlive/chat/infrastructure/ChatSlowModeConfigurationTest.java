package com.ssafy.bidlive.chat.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Duration;

import com.ssafy.bidlive.chat.application.ChatSlowModeChecker;
import com.ssafy.bidlive.chat.infrastructure.redis.RedisChatSlowModeChecker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;

/** 채팅 슬로우 모드 설정이 Redis 기반 Application Port를 생성하는지 검증한다. */
class ChatSlowModeConfigurationTest {

	private final ApplicationContextRunner contextRunner =
		new ApplicationContextRunner()
			.withBean(
				StringRedisTemplate.class,
				() -> mock(StringRedisTemplate.class)
			)
			.withUserConfiguration(ChatSlowModeConfiguration.class);

	@Test
	void createsRedisSlowModeCheckerWithBoundProperties() {
		contextRunner
			.withPropertyValues(
				"app.chat.slow-mode.interval=750ms"
			)
			.run(context -> {
				assertThat(context)
					.hasSingleBean(ChatSlowModeChecker.class)
					.hasSingleBean(ChatSlowModeProperties.class);
				assertThat(context.getBean(ChatSlowModeChecker.class))
					.isInstanceOf(RedisChatSlowModeChecker.class);
				assertThat(
					context.getBean(ChatSlowModeProperties.class)
						.getInterval()
				).isEqualTo(Duration.ofMillis(750));
			});
	}
}
