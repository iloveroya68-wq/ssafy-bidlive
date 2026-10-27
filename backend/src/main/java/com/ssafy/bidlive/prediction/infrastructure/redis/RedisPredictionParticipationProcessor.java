package com.ssafy.bidlive.prediction.infrastructure.redis;

import java.time.OffsetDateTime;

import com.ssafy.bidlive.common.support.redis.RealtimeAuctionRedisKey;
import com.ssafy.bidlive.prediction.application.PredictionParticipationCommand;
import com.ssafy.bidlive.prediction.application.PredictionParticipationProcessor;
import com.ssafy.bidlive.prediction.application.PredictionParticipationResult;
import com.ssafy.bidlive.prediction.domain.PredictionParticipationFailureCode;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

/** Redis Lua로 경매·참여·BETTING 잔액 검증과 스테이크 차감을 원자 처리한다. */
@Repository
public class RedisPredictionParticipationProcessor
	implements PredictionParticipationProcessor {

	private static final DefaultRedisScript<String> PROCESS_SCRIPT =
		createScript();

	private final StringRedisTemplate redisTemplate;

	public RedisPredictionParticipationProcessor(
		StringRedisTemplate redisTemplate
	) {
		this.redisTemplate = redisTemplate;
	}

	@Override
	public PredictionParticipationResult process(
		PredictionParticipationCommand command
	) {
		String rawResult = redisTemplate.execute(
			PROCESS_SCRIPT,
			RealtimeAuctionRedisKey.predictionParticipationKeys(
				command.auctionId(),
				command.clientRequestId()
			),
			command.memberId().toString(),
			command.auctionId().toString(),
			command.predictedPrice().toString(),
			command.stakeAmount().toString(),
			Long.toString(command.requestedAt().toInstant().toEpochMilli()),
			command.requestedAt().toString()
		);

		return parseResult(rawResult);
	}

	private PredictionParticipationResult parseResult(String rawResult) {
		if (rawResult == null) {
			throw new IllegalStateException(
				"Redis returned no prediction participation result"
			);
		}

		String[] values = rawResult.split("\\|", -1);
		try {
			if (values.length == 2 && "ERR".equals(values[0])) {
				return PredictionParticipationResult.rejected(
					PredictionParticipationFailureCode.valueOf(values[1])
				);
			}
			if (values.length == 5 && "OK".equals(values[0])) {
				return PredictionParticipationResult.accepted(
					OffsetDateTime.parse(values[1]),
					Long.parseLong(values[2]),
					Long.parseLong(values[3]),
					parseDuplicated(values[4])
				);
			}
		} catch (IllegalArgumentException exception) {
			throw new IllegalStateException(
				"Redis returned an invalid prediction participation result: "
					+ rawResult,
				exception
			);
		}

		throw new IllegalStateException(
			"Redis returned an unknown prediction participation result: "
				+ rawResult
		);
	}

	private boolean parseDuplicated(String value) {
		if ("true".equals(value)) {
			return true;
		}
		if ("false".equals(value)) {
			return false;
		}
		throw new IllegalArgumentException(
			"duplicated must be true or false"
		);
	}

	private static DefaultRedisScript<String> createScript() {
		DefaultRedisScript<String> script = new DefaultRedisScript<>();
		script.setLocation(
			new ClassPathResource(
				"redis/lua/prediction-participation-process.lua"
			)
		);
		script.setResultType(String.class);
		return script;
	}
}
