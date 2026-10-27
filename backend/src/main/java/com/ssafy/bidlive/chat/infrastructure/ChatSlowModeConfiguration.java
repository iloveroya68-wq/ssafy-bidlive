package com.ssafy.bidlive.chat.infrastructure;

import com.ssafy.bidlive.chat.application.ChatSlowModeChecker;
import com.ssafy.bidlive.chat.infrastructure.redis.RedisChatSlowModeChecker;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/** 채팅 슬로우 모드 설정과 Redis 구현을 Application Port로 조립한다. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ChatSlowModeProperties.class)
public class ChatSlowModeConfiguration {

	@Bean
	ChatSlowModeChecker chatSlowModeChecker(
		StringRedisTemplate redisTemplate,
		ChatSlowModeProperties properties
	) {
		return new RedisChatSlowModeChecker(redisTemplate, properties);
	}
}
