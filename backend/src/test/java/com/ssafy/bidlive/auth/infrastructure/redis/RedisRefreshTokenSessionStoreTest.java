package com.ssafy.bidlive.auth.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;

import com.ssafy.bidlive.auth.application.VerifiedRefreshToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

/** Refresh Token Redis 키, TTL, 세션 일치와 원자적 회전 동작을 검증한다. */
@ExtendWith(MockitoExtension.class)
class RedisRefreshTokenSessionStoreTest {

	@Mock
	private StringRedisTemplate redisTemplate;

	@Mock
	private ValueOperations<String, String> valueOperations;

	private RedisRefreshTokenSessionStore store;

	@BeforeEach
	void setUp() {
		store = new RedisRefreshTokenSessionStore(redisTemplate);
	}

	@Test
	void savesSessionWithStandardKeyAndRefreshTokenTtl() {
		VerifiedRefreshToken token = new VerifiedRefreshToken(42L, "token-id");
		Duration ttl = Duration.ofDays(14);
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);

		store.save(token, ttl);

		verify(valueOperations).set(
			"bidlive:auth:{42}:refresh-token:token-id",
			"42",
			ttl
		);
	}

	@Test
	void matchesOnlyStoredMemberSession() {
		VerifiedRefreshToken token = new VerifiedRefreshToken(42L, "token-id");
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.get(
			"bidlive:auth:{42}:refresh-token:token-id"
		)).thenReturn("42", "99", null);

		assertThat(store.matches(token)).isTrue();
		assertThat(store.matches(token)).isFalse();
		assertThat(store.matches(token)).isFalse();
	}

	@Test
	@SuppressWarnings("unchecked")
	void rotatesSessionAtomicallyWithinMemberHashSlot() {
		VerifiedRefreshToken current =
			new VerifiedRefreshToken(42L, "current-id");
		VerifiedRefreshToken replacement =
			new VerifiedRefreshToken(42L, "replacement-id");
		when(redisTemplate.execute(
			any(RedisScript.class),
			any(List.class),
			eq("42"),
			eq("42"),
			eq("120000"),
			eq("replacement-token-value"),
			eq("10000")
		)).thenReturn(1L);

		boolean rotated = store.rotate(
			current,
			replacement,
			"replacement-token-value",
			Duration.ofMinutes(2),
			Duration.ofSeconds(10)
		);

		assertThat(rotated).isTrue();
		ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.forClass(List.class);
		verify(redisTemplate).execute(
			any(RedisScript.class),
			keysCaptor.capture(),
			eq("42"),
			eq("42"),
			eq("120000"),
			eq("replacement-token-value"),
			eq("10000")
		);
		// 유예 기록 키가 **같은 회원 Hash Tag 안**이어야 한다 — 벗어나면 클러스터에서 CROSSSLOT이다
		assertThat(keysCaptor.getValue()).containsExactly(
			"bidlive:auth:{42}:refresh-token:current-id",
			"bidlive:auth:{42}:refresh-token:replacement-id",
			"bidlive:auth:{42}:refresh-token:current-id:rotated"
		);
	}

	@Test
	void rejectsCrossMemberRotationBeforeRedisCall() {
		boolean rotated = store.rotate(
			new VerifiedRefreshToken(42L, "current-id"),
			new VerifiedRefreshToken(43L, "replacement-id"),
			"replacement-token-value",
			Duration.ofMinutes(2),
			Duration.ofSeconds(10)
		);

		assertThat(rotated).isFalse();
		verifyNoInteractions(valueOperations);
	}

	@Test
	void deletesSessionByStandardKeyEvenWhenSessionMayAlreadyBeAbsent() {
		VerifiedRefreshToken token = new VerifiedRefreshToken(42L, "token-id");

		store.delete(token);

		verify(redisTemplate).delete(
			"bidlive:auth:{42}:refresh-token:token-id"
		);
	}

	/**
	 * 유예 기록 조회 (S15P11A704-693). 키를 잘못 만들면 조회가 늘 비어 구제가 통째로 죽는데,
	 * 증상은 「가끔 로그아웃」 그대로라 눈으로는 고쳐졌는지 알 수 없다.
	 */
	@Test
	void readsRotatedReplacementFromSuffixedKey() {
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.get("bidlive:auth:{42}:refresh-token:current-id:rotated"))
			.thenReturn("winner-refresh-token");

		assertThat(store.findRotatedReplacement(new VerifiedRefreshToken(42L, "current-id")))
			.contains("winner-refresh-token");
	}

	@Test
	void returnsEmptyWhenRotationGraceExpired() {
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.get("bidlive:auth:{42}:refresh-token:current-id:rotated"))
			.thenReturn(null);

		assertThat(store.findRotatedReplacement(new VerifiedRefreshToken(42L, "current-id")))
			.isEmpty();
	}
}
