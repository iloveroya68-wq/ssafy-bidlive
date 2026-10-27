package com.ssafy.bidlive.auth.infrastructure.redis;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import com.ssafy.bidlive.auth.application.RefreshTokenSessionStore;
import com.ssafy.bidlive.auth.application.VerifiedRefreshToken;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

/** Refresh Token 세션을 TTL로 저장하고 기존 세션 폐기와 교체를 원자적으로 수행한다. */
@Repository
public class RedisRefreshTokenSessionStore implements RefreshTokenSessionStore {

	/**
	 * 교체와 유예 기록을 <b>한 번에</b> 쓴다 (S15P11A704-693).
	 *
	 * <p>유예 기록을 스크립트 밖에서 따로 쓰면 그 사이에 도착한 racer가 「기존 키는 이미 지워졌는데
	 * 유예 기록은 아직 없는」 창을 만나 그대로 401을 받는다 — 고치려던 증상이 좁아지기만 하고 남는다.
	 *
	 * <p>`KEYS[3]`(유예 키)에는 <b>새 Refresh Token 문자열</b>을 담는다. 회원 번호가 아니라 토큰을
	 * 담는 이유는, 뒤늦게 온 요청에게 새로 발급하는 대신 <b>이미 발급된 그 토큰</b>을 그대로
	 * 돌려주기 위해서다. 새로 발급하면 회전 사슬이 둘로 갈라져 어느 쪽 쿠키가 살아남는지가
	 * 응답 도착 순서에 달리게 된다.
	 */
	private static final DefaultRedisScript<Long> ROTATE_SCRIPT =
		new DefaultRedisScript<>(
			"""
				local currentMemberId = redis.call('GET', KEYS[1])
				if not currentMemberId or currentMemberId ~= ARGV[1] then
					return 0
				end
				redis.call('DEL', KEYS[1])
				redis.call('SET', KEYS[2], ARGV[2], 'PX', ARGV[3])
				redis.call('SET', KEYS[3], ARGV[4], 'PX', ARGV[5])
				return 1
				""",
			Long.class
		);

	/** 유예 기록 키 — 지워진 세션 키에 접미사를 붙여 같은 회원 Hash Tag 안에 둔다 */
	private static final String ROTATED_KEY_SUFFIX = ":rotated";

	private final StringRedisTemplate redisTemplate;

	public RedisRefreshTokenSessionStore(StringRedisTemplate redisTemplate) {
		this.redisTemplate = redisTemplate;
	}

	@Override
	public void save(VerifiedRefreshToken token, Duration ttl) {
		redisTemplate.opsForValue().set(
			RefreshTokenRedisKey.from(token),
			token.memberId().toString(),
			ttl
		);
	}

	@Override
	public boolean matches(VerifiedRefreshToken token) {
		String storedMemberId = redisTemplate.opsForValue()
			.get(RefreshTokenRedisKey.from(token));
		return token.memberId().toString().equals(storedMemberId);
	}

	@Override
	public boolean rotate(
		VerifiedRefreshToken currentToken,
		VerifiedRefreshToken replacementToken,
		String replacementTokenValue,
		Duration replacementTtl,
		Duration graceTtl
	) {
		if (!currentToken.memberId().equals(replacementToken.memberId())) {
			return false;
		}

		Long result = redisTemplate.execute(
			ROTATE_SCRIPT,
			List.of(
				RefreshTokenRedisKey.from(currentToken),
				RefreshTokenRedisKey.from(replacementToken),
				RefreshTokenRedisKey.from(currentToken) + ROTATED_KEY_SUFFIX
			),
			currentToken.memberId().toString(),
			replacementToken.memberId().toString(),
			Long.toString(replacementTtl.toMillis()),
			replacementTokenValue,
			Long.toString(graceTtl.toMillis())
		);
		return Long.valueOf(1L).equals(result);
	}

	@Override
	public Optional<String> findRotatedReplacement(VerifiedRefreshToken rotatedToken) {
		return Optional.ofNullable(
			redisTemplate.opsForValue()
				.get(RefreshTokenRedisKey.from(rotatedToken) + ROTATED_KEY_SUFFIX)
		);
	}

	@Override
	public void delete(VerifiedRefreshToken token) {
		redisTemplate.delete(RefreshTokenRedisKey.from(token));
	}
}
