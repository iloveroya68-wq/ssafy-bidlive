package com.ssafy.bidlive.auction.infrastructure.redis;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import com.ssafy.bidlive.auction.application.AuctionExtensionCommand;
import com.ssafy.bidlive.auction.application.AuctionExtensionProcessingResult;
import com.ssafy.bidlive.auction.application.AuctionExtensionProcessor;
import com.ssafy.bidlive.auction.domain.AuctionExtensionErrorCode;
import com.ssafy.bidlive.common.support.redis.RealtimeAuctionRedisKey;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

/** Redis Lua로 연장 검증과 마감 시각·횟수·버전 갱신을 원자적으로 수행한다. */
@Repository
public class RedisAuctionExtensionProcessor
	implements AuctionExtensionProcessor {

	private static final DefaultRedisScript<String> EXTEND_SCRIPT =
		createScript();

	private final StringRedisTemplate redisTemplate;

	public RedisAuctionExtensionProcessor(
		StringRedisTemplate redisTemplate
	) {
		this.redisTemplate = Objects.requireNonNull(
			redisTemplate,
			"redisTemplate"
		);
	}

	@Override
	public AuctionExtensionProcessingResult extend(
		AuctionExtensionCommand command
	) {
		Objects.requireNonNull(command, "command");
		String rawResult = redisTemplate.execute(
			EXTEND_SCRIPT,
			List.of(RealtimeAuctionRedisKey.auctionStateKey(
				command.auctionId()
			)),
			Long.toString(command.requesterId()),
			Long.toString(command.requestedAt().toEpochMilli())
		);
		return parseResult(rawResult);
	}

	private static AuctionExtensionProcessingResult parseResult(
		String rawResult
	) {
		if (rawResult == null) {
			throw invalidResult("null");
		}

		String[] values = rawResult.split("\\|", -1);
		try {
			if (values.length == 2 && "ERR".equals(values[0])) {
				return AuctionExtensionProcessingResult.rejected(
					AuctionExtensionErrorCode.valueOf(values[1])
				);
			}
			if (values.length == 2 && "SYS".equals(values[0])) {
				throw invalidResult(rawResult);
			}
			if (values.length == 6 && "OK".equals(values[0])) {
				return AuctionExtensionProcessingResult.accepted(
					Instant.ofEpochMilli(Long.parseLong(values[1])),
					Instant.ofEpochMilli(Long.parseLong(values[2])),
					Integer.parseInt(values[3]),
					Integer.parseInt(values[4]),
					Long.parseLong(values[5])
				);
			}
		} catch (IllegalArgumentException exception) {
			throw new IllegalStateException(
				"Redis returned an invalid auction extension result: "
					+ rawResult,
				exception
			);
		}
		throw invalidResult(rawResult);
	}

	private static IllegalStateException invalidResult(String rawResult) {
		return new IllegalStateException(
			"Redis returned an unknown auction extension result: "
				+ rawResult
		);
	}

	private static DefaultRedisScript<String> createScript() {
		DefaultRedisScript<String> script = new DefaultRedisScript<>();
		script.setLocation(new ClassPathResource(
			"redis/lua/extend-auction.lua"
		));
		script.setResultType(String.class);
		return script;
	}
}
