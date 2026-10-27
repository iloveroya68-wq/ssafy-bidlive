package com.ssafy.bidlive.auction.infrastructure.redis;

import java.util.List;
import java.util.Objects;

import com.ssafy.bidlive.auction.application.AuctionSettlementClaimCommand;
import com.ssafy.bidlive.auction.application.AuctionSettlementClaimProcessor;
import com.ssafy.bidlive.auction.application.AuctionSettlementClaimResult;
import com.ssafy.bidlive.common.support.redis.RealtimeAuctionRedisKey;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

/** Redis Lua로 마감 시각 검증과 LIVE에서 SETTLING으로의 선점을 원자 처리한다. */
@Repository
public class RedisAuctionSettlementClaimProcessor
	implements AuctionSettlementClaimProcessor {

	private static final DefaultRedisScript<String> CLAIM_SCRIPT =
		createScript();

	private final StringRedisTemplate redisTemplate;

	public RedisAuctionSettlementClaimProcessor(
		StringRedisTemplate redisTemplate
	) {
		this.redisTemplate = Objects.requireNonNull(
			redisTemplate,
			"redisTemplate"
		);
	}

	@Override
	public AuctionSettlementClaimResult claim(
		AuctionSettlementClaimCommand command
	) {
		Objects.requireNonNull(command, "command");
		String rawResult = redisTemplate.execute(
			CLAIM_SCRIPT,
			List.of(RealtimeAuctionRedisKey.auctionStateKey(
				command.auctionId()
			)),
			Long.toString(command.requestedAt().toEpochMilli()),
			command.requestedAt().toString()
		);
		return parseResult(rawResult);
	}

	private static AuctionSettlementClaimResult parseResult(
		String rawResult
	) {
		if (rawResult == null) {
			throw invalidResult("null");
		}

		String[] values = rawResult.split("\\|", -1);
		try {
			if (values.length == 2 && "OK".equals(values[0])) {
				AuctionSettlementClaimResult result =
					AuctionSettlementClaimResult.valueOf(values[1]);
				if (result != AuctionSettlementClaimResult.CLAIMED) {
					throw new IllegalArgumentException(
						"result prefix does not match claim outcome"
					);
				}
				return result;
			}
			if (values.length == 2 && "ERR".equals(values[0])) {
				AuctionSettlementClaimResult result =
					AuctionSettlementClaimResult.valueOf(values[1]);
				if (result == AuctionSettlementClaimResult.CLAIMED) {
					throw new IllegalArgumentException(
						"result prefix does not match claim outcome"
					);
				}
				return result;
			}
		} catch (IllegalArgumentException exception) {
			throw new IllegalStateException(
				"Redis returned an invalid auction settlement claim result: "
					+ rawResult,
				exception
			);
		}
		throw invalidResult(rawResult);
	}

	private static IllegalStateException invalidResult(String rawResult) {
		return new IllegalStateException(
			"Redis returned an unknown auction settlement claim result: "
				+ rawResult
		);
	}

	private static DefaultRedisScript<String> createScript() {
		DefaultRedisScript<String> script = new DefaultRedisScript<>();
		script.setLocation(new ClassPathResource(
			"redis/lua/claim-auction-settlement.lua"
		));
		script.setResultType(String.class);
		return script;
	}
}
