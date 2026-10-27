package com.ssafy.bidlive.auth.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;

import com.ssafy.bidlive.auth.application.LoginCodeConsumeResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

@ExtendWith(MockitoExtension.class)
class RedisOneTimeLoginCodeStoreTest {

	@Mock
	private StringRedisTemplate redisTemplate;

	private RedisOneTimeLoginCodeStore store;

	@BeforeEach
	void setUp() {
		store = new RedisOneTimeLoginCodeStore(redisTemplate);
	}

	@Test
	@SuppressWarnings("unchecked")
	void storesOnlyHashedCodeKeysWithShortTtl() {
		Duration ttl = Duration.ofMinutes(2);

		store.save("plain-one-time-login-code", 42L, ttl);

		ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.forClass(List.class);
		verify(redisTemplate).execute(
			any(RedisScript.class),
			keysCaptor.capture(),
			eq("42"),
			eq("120000"),
			eq("720000")
		);
		assertThat(keysCaptor.getValue())
			.hasSize(3)
			.allSatisfy(key -> assertThat(key)
				.startsWith("auth:oauth2:login-code:")
				.doesNotContain("plain-one-time-login-code"));
	}

	@Test
	@SuppressWarnings("unchecked")
	void consumesCodeAtomicallyAndReturnsMemberId() {
		when(redisTemplate.execute(
			any(RedisScript.class),
			any(List.class),
			eq("600000")
		)).thenReturn("CONSUMED:77");

		LoginCodeConsumeResult result = store.consume("secret");

		assertThat(result.status()).isEqualTo(LoginCodeConsumeResult.Status.CONSUMED);
		assertThat(result.memberId()).isEqualTo(77L);
	}

	@Test
	@SuppressWarnings("unchecked")
	void distinguishesAlreadyUsedExpiredAndUnknownCodes() {
		when(redisTemplate.execute(
			any(RedisScript.class),
			any(List.class),
			eq("600000")
		)).thenReturn("ALREADY_USED", "EXPIRED", "NOT_FOUND");

		assertThat(store.consume("used").status())
			.isEqualTo(LoginCodeConsumeResult.Status.ALREADY_USED);
		assertThat(store.consume("expired").status())
			.isEqualTo(LoginCodeConsumeResult.Status.EXPIRED);
		assertThat(store.consume("unknown").status())
			.isEqualTo(LoginCodeConsumeResult.Status.NOT_FOUND);
	}
}
