package com.ssafy.bidlive.common.config;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Instant;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/** 공통 RedisTemplate의 Key·Value 직렬화 정책을 검증한다. */
class RedisConfigTest {

	private final RedisConfig redisConfig = new RedisConfig();

	@Test
	void configuresStringKeysAndJsonValues() {
		RedisTemplate<String, Object> redisTemplate = redisConfig.redisTemplate(
			mock(RedisConnectionFactory.class),
			new ObjectMapper().findAndRegisterModules()
		);

		assertThat(redisTemplate.getKeySerializer()).isSameAs(StringRedisSerializer.UTF_8);
		assertThat(redisTemplate.getHashKeySerializer()).isSameAs(StringRedisSerializer.UTF_8);
		assertThat(redisTemplate.getValueSerializer())
			.isInstanceOf(GenericJackson2JsonRedisSerializer.class);
		assertThat(redisTemplate.getHashValueSerializer())
			.isSameAs(redisTemplate.getValueSerializer());
	}

	@Test
	void preservesTypeAndJavaTimeValuesDuringJsonRoundTrip() {
		RedisTemplate<String, Object> redisTemplate = redisConfig.redisTemplate(
			mock(RedisConnectionFactory.class),
			new ObjectMapper().findAndRegisterModules()
		);
		SampleAuctionState expected = new SampleAuctionState(
			42L,
			"LIVE",
			Instant.parse("2026-07-27T12:34:56Z")
		);

		RedisSerializer<Object> valueSerializer = valueSerializer(redisTemplate);
		byte[] serialized = valueSerializer.serialize(expected);
		Object restored = valueSerializer.deserialize(serialized);

		assertThat(new String(serialized, UTF_8))
			.contains("\"@type\"")
			.contains("\"status\":\"LIVE\"");
		assertThat(restored).isEqualTo(expected);
	}

	@SuppressWarnings("unchecked")
	private RedisSerializer<Object> valueSerializer(RedisTemplate<String, Object> redisTemplate) {
		return (RedisSerializer<Object>) redisTemplate.getValueSerializer();
	}

	private record SampleAuctionState(Long auctionId, String status, Instant updatedAt) {
	}
}
