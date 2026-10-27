package com.ssafy.bidlive.bid.infrastructure.redis;

import java.time.OffsetDateTime;

import com.ssafy.bidlive.bid.application.BidCommand;
import com.ssafy.bidlive.bid.application.BidProcessingResult;
import com.ssafy.bidlive.bid.application.BidProcessor;
import com.ssafy.bidlive.bid.domain.BidFailureCode;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

/** Redis Lua로 입찰 검증, 회원 전역 지갑 차액 홀드와 경매 상태 갱신을 원자적으로 수행한다. */
@Repository
public class RedisBidProcessor implements BidProcessor {

	private static final DefaultRedisScript<String> PROCESS_SCRIPT = createScript();

	private final StringRedisTemplate redisTemplate;

	public RedisBidProcessor(StringRedisTemplate redisTemplate) {
		this.redisTemplate = redisTemplate;
	}

	@Override
	public BidProcessingResult process(BidCommand command) {
		String rawResult = redisTemplate.execute(
			PROCESS_SCRIPT,
			BidRedisKey.processingKeys(command.auctionId()),
			command.memberId().toString(),
			command.amount().toString(),
			command.requestId(),
			Long.toString(command.requestedAt().toInstant().toEpochMilli()),
			command.requestedAt().toString()
		);

		return parseResult(rawResult);
	}

	private BidProcessingResult parseResult(String rawResult) {
		if (rawResult == null) {
			throw new IllegalStateException("Redis returned no bid processing result");
		}

		String[] values = rawResult.split("\\|", -1);
		try {
			if (values.length == 2 && "ERR".equals(values[0])) {
				return BidProcessingResult.rejected(BidFailureCode.valueOf(values[1]));
			}
			if (values.length == 9 && "OK".equals(values[0])) {
				return BidProcessingResult.accepted(
					Long.parseLong(values[1]),
					Long.parseLong(values[2]),
					Long.parseLong(values[3]),
					Long.parseLong(values[4]),
					Long.parseLong(values[5]),
					OffsetDateTime.parse(values[6]),
					Long.parseLong(values[7]),
					Long.parseLong(values[8])
				);
			}
		} catch (IllegalArgumentException exception) {
			throw new IllegalStateException("Redis returned an invalid bid processing result: " + rawResult, exception);
		}

		throw new IllegalStateException("Redis returned an unknown bid processing result: " + rawResult);
	}

	private static DefaultRedisScript<String> createScript() {
		DefaultRedisScript<String> script = new DefaultRedisScript<>();
		script.setLocation(new ClassPathResource("redis/lua/bid-process.lua"));
		script.setResultType(String.class);
		return script;
	}
}
