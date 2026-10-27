package com.ssafy.bidlive.prediction.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.ssafy.bidlive.prediction.application.PredictionParticipationCommand;
import com.ssafy.bidlive.prediction.application.PredictionParticipationResult;
import com.ssafy.bidlive.prediction.domain.PredictionParticipationFailureCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

/** 예측 참여 Lua 호출 인자와 성공·실패 결과 변환을 검증한다. */
@ExtendWith(MockitoExtension.class)
class RedisPredictionParticipationProcessorTest {

	private static final OffsetDateTime REQUESTED_AT =
		OffsetDateTime.parse("2026-07-29T20:59:00+09:00");
	private static final UUID CLIENT_REQUEST_ID =
		UUID.fromString("2b8bb687-6d5e-4e09-b651-b88727266a51");

	@Mock
	private StringRedisTemplate redisTemplate;

	private RedisPredictionParticipationProcessor processor;

	@BeforeEach
	void setUp() {
		processor = new RedisPredictionParticipationProcessor(redisTemplate);
	}

	@Test
	@SuppressWarnings("unchecked")
	void sendsSharedAuctionKeysAndServerTimeAndMapsAcceptedResult() {
		when(redisTemplate.execute(
			any(RedisScript.class),
			any(List.class),
			eq("25"),
			eq("100"),
			eq("74000"),
			eq("300"),
			eq(Long.toString(REQUESTED_AT.toInstant().toEpochMilli())),
			eq(REQUESTED_AT.toString())
		)).thenReturn("OK|2026-07-29T20:59:00+09:00|8|700|false");

		PredictionParticipationResult result = processor.process(command());

		ArgumentCaptor<List<String>> keysCaptor =
			ArgumentCaptor.forClass(List.class);
		verify(redisTemplate).execute(
			any(RedisScript.class),
			keysCaptor.capture(),
			eq("25"),
			eq("100"),
			eq("74000"),
			eq("300"),
			eq(Long.toString(REQUESTED_AT.toInstant().toEpochMilli())),
			eq(REQUESTED_AT.toString())
		);
		assertThat(keysCaptor.getValue()).containsExactly(
			"bid:{bid}:auction:100:state",
			"bid:{bid}:auction:100:participation",
			"bid:{bid}:prediction:processed-request:"
				+ CLIENT_REQUEST_ID,
			"bid:{bid}:wallet:betting-available-balances"
		);
		assertThat(keysCaptor.getValue())
			.allSatisfy(key -> assertThat(key).contains("{bid}"));
		assertThat(result.accepted()).isTrue();
		assertThat(result.submittedAt()).isEqualTo(REQUESTED_AT);
		assertThat(result.participantCount()).isEqualTo(8L);
		assertThat(result.availableBalance()).isEqualTo(700L);
		assertThat(result.duplicated()).isFalse();
		assertThat(result.failureCode()).isNull();
	}

	@ParameterizedTest
	@EnumSource(PredictionParticipationFailureCode.class)
	@SuppressWarnings("unchecked")
	void mapsEverySpecifiedFailureCode(
		PredictionParticipationFailureCode failureCode
	) {
		when(redisTemplate.execute(
			any(RedisScript.class),
			any(List.class),
			any(),
			any(),
			any(),
			any(),
			any(),
			any()
		)).thenReturn("ERR|" + failureCode.name());

		PredictionParticipationResult result = processor.process(command());

		assertThat(result.accepted()).isFalse();
		assertThat(result.failureCode()).isEqualTo(failureCode);
	}

	@Test
	@SuppressWarnings("unchecked")
	void rejectsMissingAndMalformedRedisResults() {
		when(redisTemplate.execute(
			any(RedisScript.class),
			any(List.class),
			any(),
			any(),
			any(),
			any(),
			any(),
			any()
		)).thenReturn(null, "ERR|INVALID_AUCTION_STATE");

		assertThatThrownBy(() -> processor.process(command()))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("no prediction participation result");
		assertThatThrownBy(() -> processor.process(command()))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining(
				"invalid prediction participation result"
			);
	}

	private PredictionParticipationCommand command() {
		return new PredictionParticipationCommand(
			100L,
			25L,
			74_000L,
			300L,
			CLIENT_REQUEST_ID,
			REQUESTED_AT
		);
	}
}
