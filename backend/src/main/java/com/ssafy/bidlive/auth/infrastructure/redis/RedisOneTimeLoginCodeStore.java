package com.ssafy.bidlive.auth.infrastructure.redis;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;

import com.ssafy.bidlive.auth.application.LoginCodeConsumeResult;
import com.ssafy.bidlive.auth.application.OneTimeLoginCodeStore;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RedisOneTimeLoginCodeStore implements OneTimeLoginCodeStore {

	private static final String ACTIVE_KEY_PREFIX = "auth:oauth2:login-code:active:";
	private static final String ISSUED_KEY_PREFIX = "auth:oauth2:login-code:issued:";
	private static final String CONSUMED_KEY_PREFIX = "auth:oauth2:login-code:consumed:";
	private static final Duration RESULT_RETENTION = Duration.ofMinutes(10);

	private static final DefaultRedisScript<String> SAVE_SCRIPT = new DefaultRedisScript<>(
		"""
			redis.call('SET', KEYS[1], ARGV[1], 'PX', ARGV[2])
			redis.call('SET', KEYS[2], '1', 'PX', ARGV[3])
			redis.call('DEL', KEYS[3])
			return 'OK'
			""",
		String.class
	);

	private static final DefaultRedisScript<String> CONSUME_SCRIPT = new DefaultRedisScript<>(
		"""
			local memberId = redis.call('GET', KEYS[1])
			if memberId then
				redis.call('DEL', KEYS[1])
				local markerTtl = redis.call('PTTL', KEYS[2])
				if markerTtl < 1 then
					markerTtl = tonumber(ARGV[1])
				end
				redis.call('SET', KEYS[3], '1', 'PX', markerTtl)
				return 'CONSUMED:' .. memberId
			end
			if redis.call('EXISTS', KEYS[3]) == 1 then
				return 'ALREADY_USED'
			end
			if redis.call('EXISTS', KEYS[2]) == 1 then
				return 'EXPIRED'
			end
			return 'NOT_FOUND'
			""",
		String.class
	);

	private final StringRedisTemplate redisTemplate;

	public RedisOneTimeLoginCodeStore(StringRedisTemplate redisTemplate) {
		this.redisTemplate = redisTemplate;
	}

	@Override
	public void save(String loginCode, Long memberId, Duration ttl) {
		String codeHash = hash(loginCode);
		redisTemplate.execute(
			SAVE_SCRIPT,
			keys(codeHash),
			memberId.toString(),
			Long.toString(ttl.toMillis()),
			Long.toString(ttl.plus(RESULT_RETENTION).toMillis())
		);
	}

	@Override
	public LoginCodeConsumeResult consume(String loginCode) {
		String codeHash = hash(loginCode);
		String result = redisTemplate.execute(
			CONSUME_SCRIPT,
			keys(codeHash),
			Long.toString(RESULT_RETENTION.toMillis())
		);

		if (result != null && result.startsWith("CONSUMED:")) {
			return LoginCodeConsumeResult.consumed(
				Long.valueOf(result.substring("CONSUMED:".length()))
			);
		}
		if ("ALREADY_USED".equals(result)) {
			return LoginCodeConsumeResult.alreadyUsed();
		}
		if ("EXPIRED".equals(result)) {
			return LoginCodeConsumeResult.expired();
		}
		return LoginCodeConsumeResult.notFound();
	}

	private List<String> keys(String codeHash) {
		return List.of(
			ACTIVE_KEY_PREFIX + codeHash,
			ISSUED_KEY_PREFIX + codeHash,
			CONSUMED_KEY_PREFIX + codeHash
		);
	}

	private String hash(String loginCode) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(loginCode.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash);
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is not available", exception);
		}
	}
}
