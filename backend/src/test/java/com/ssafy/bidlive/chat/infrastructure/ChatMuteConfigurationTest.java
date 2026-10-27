package com.ssafy.bidlive.chat.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.ssafy.bidlive.chat.application.ChatMuteReader;
import com.ssafy.bidlive.chat.infrastructure.redis.RedisChatMuteReader;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;

/** 채팅 mute 설정이 Redis 기반 Application Port를 생성하는지 검증한다. */
class ChatMuteConfigurationTest {

	private final ApplicationContextRunner contextRunner =
		new ApplicationContextRunner()
			.withBean(
				StringRedisTemplate.class,
				() -> mock(StringRedisTemplate.class)
			)
			.withUserConfiguration(ChatMuteConfiguration.class);

	@Test
	void createsRedisChatMuteReader() {
		contextRunner.run(context -> {
			assertThat(context).hasSingleBean(ChatMuteReader.class);
			assertThat(context.getBean(ChatMuteReader.class))
				.isInstanceOf(RedisChatMuteReader.class);
		});
	}
}
