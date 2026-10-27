package com.ssafy.bidlive.auction.infrastructure.redis;

import java.util.List;
import java.util.Objects;

import com.ssafy.bidlive.auction.application.AuctionSettlementCompletionCommand;
import com.ssafy.bidlive.auction.application.AuctionSettlementCompletionProcessor;
import com.ssafy.bidlive.auction.application.AuctionSettlementCompletionResult;
import com.ssafy.bidlive.common.support.redis.RealtimeAuctionRedisKey;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

/** 커밋된 정산만 Redis SETTLING에서 ENDED로 원자 전환한다. */
@Repository
public class RedisAuctionSettlementCompletionProcessor
	implements AuctionSettlementCompletionProcessor {

	private static final DefaultRedisScript<String> COMPLETION_SCRIPT =
		createScript();

	private final StringRedisTemplate redisTemplate;

	public RedisAuctionSettlementCompletionProcessor(
		StringRedisTemplate redisTemplate
	) {
		this.redisTemplate = Objects.requireNonNull(
			redisTemplate,
			"redisTemplate"
		);
	}

	@Override
	public AuctionSettlementCompletionResult complete(
		AuctionSettlementCompletionCommand command
	) {
		Objects.requireNonNull(command, "command");
		String rawResult = redisTemplate.execute(
			COMPLETION_SCRIPT,
			List.of(RealtimeAuctionRedisKey.auctionStateKey(
				command.auctionId()
			)),
			command.completedAt().toString()
		);
		return parseResult(rawResult);
	}

	private static AuctionSettlementCompletionResult parseResult(
		String rawResult
	) {
		if (rawResult == null) {
			throw invalidResult("null");
		}
		String[] values = rawResult.split("\\|", -1);
		if (values.length != 2) {
			throw invalidResult(rawResult);
		}
		try {
			AuctionSettlementCompletionResult result =
				AuctionSettlementCompletionResult.valueOf(values[1]);
			if ("OK".equals(values[0]) == result.completed()) {
				return result;
			}
		} catch (IllegalArgumentException exception) {
			throw new IllegalStateException(
				"Redis returned an invalid settlement completion result: "
					+ rawResult,
				exception
			);
		}
		throw invalidResult(rawResult);
	}

	private static IllegalStateException invalidResult(String rawResult) {
		return new IllegalStateException(
			"Redis returned an unknown settlement completion result: "
				+ rawResult
		);
	}

	private static DefaultRedisScript<String> createScript() {
		DefaultRedisScript<String> script = new DefaultRedisScript<>();
		script.setLocation(new ClassPathResource(
			"redis/lua/complete-auction-settlement.lua"
		));
		script.setResultType(String.class);
		return script;
	}
}
