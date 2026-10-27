package com.ssafy.bidlive.chat.infrastructure;

import com.ssafy.bidlive.chat.application.ChatMuteReader;
import com.ssafy.bidlive.chat.infrastructure.redis.RedisChatMuteReader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Redis 채팅 mute 조회 구현을 Application Port로 조립한다. */
@Configuration(proxyBeanMethods = false)
public class ChatMuteConfiguration {

	@Bean
	ChatMuteReader chatMuteReader(StringRedisTemplate redisTemplate) {
		return new RedisChatMuteReader(redisTemplate);
	}
}
