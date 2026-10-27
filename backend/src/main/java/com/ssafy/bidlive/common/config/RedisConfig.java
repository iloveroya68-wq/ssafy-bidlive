package com.ssafy.bidlive.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;

/** RedisTemplate의 문자열 Key와 JSON Value 직렬화 정책을 구성한다. */
@Configuration
public class RedisConfig {

	@Bean
	public RedisTemplate<String, Object> redisTemplate(
		RedisConnectionFactory connectionFactory,
		ObjectMapper objectMapper
	) {
		RedisSerializer<String> keySerializer = RedisSerializer.string();
		RedisSerializer<Object> valueSerializer = GenericJackson2JsonRedisSerializer.builder()
			.objectMapper(objectMapper.copy())
			.defaultTyping(true)
			.typeHintPropertyName("@type")
			.build();

		RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
		redisTemplate.setConnectionFactory(connectionFactory);
		redisTemplate.setKeySerializer(keySerializer);
		redisTemplate.setHashKeySerializer(keySerializer);
		redisTemplate.setValueSerializer(valueSerializer);
		redisTemplate.setHashValueSerializer(valueSerializer);
		redisTemplate.setDefaultSerializer(valueSerializer);
		redisTemplate.afterPropertiesSet();
		return redisTemplate;
	}
}
