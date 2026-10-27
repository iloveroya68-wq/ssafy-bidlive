package com.ssafy.bidlive.bid.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;

import com.ssafy.bidlive.bid.application.BidCommand;
import com.ssafy.bidlive.bid.application.BidProcessingResult;
import com.ssafy.bidlive.bid.domain.BidFailureCode;
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

/** Redis Lua 호출 인자와 명세상 성공·실패 결과 변환을 검증한다. */
@ExtendWith(MockitoExtension.class)
class RedisBidProcessorTest {

	private static final OffsetDateTime REQUESTED_AT = OffsetDateTime.parse("2026-07-20T21:12:44+09:00");

	@Mock
	private StringRedisTemplate redisTemplate;

	private RedisBidProcessor processor;

	@BeforeEach
	void setUp() {
		processor = new RedisBidProcessor(redisTemplate);
	}

	@Test
	@SuppressWarnings("unchecked")
	void sendsAuctionAndGlobalWalletKeysToOneHashSlotAndMapsAcceptedResult() {
		when(redisTemplate.execute(
			any(RedisScript.class),
			any(List.class),
			eq("25"),
			eq("82000"),
			eq("request-1"),
			eq(Long.toString(REQUESTED_AT.toInstant().toEpochMilli())),
			eq(REQUESTED_AT.toString())
		)).thenReturn("OK|70000|82000|12000|82000|118000|2026-07-20T21:12:44+09:00|13|9");

		BidProcessingResult result = processor.process(command("request-1"));

		ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.forClass(List.class);
		verify(redisTemplate).execute(
			any(RedisScript.class),
			keysCaptor.capture(),
			eq("25"),
			eq("82000"),
			eq("request-1"),
			eq(Long.toString(REQUESTED_AT.toInstant().toEpochMilli())),
			eq(REQUESTED_AT.toString())
		);
		assertThat(keysCaptor.getValue()).containsExactly(
			"bid:{bid}:auction:100:state",
			"bid:{bid}:auction:100:member-bids",
			"bid:{bid}:auction:100:submitted-at",
			"bid:{bid}:auction:100:participation",
			"bid:{bid}:wallet:available-balances",
			"bid:{bid}:wallet:held-balances",
			"bid:{bid}:auction:100:processed-requests"
		);
		assertThat(keysCaptor.getValue())
			.allSatisfy(key -> assertThat(key).contains("{bid}"));
		assertThat(result.accepted()).isTrue();
		assertThat(result.previousBidAmount()).isEqualTo(70_000L);
		assertThat(result.currentBidAmount()).isEqualTo(82_000L);
		assertThat(result.additionalHoldAmount()).isEqualTo(12_000L);
		assertThat(result.totalHeldAmount()).isEqualTo(82_000L);
		assertThat(result.availableBalance()).isEqualTo(118_000L);
		assertThat(result.submittedAt()).isEqualTo(REQUESTED_AT);
		assertThat(result.bidCount()).isEqualTo(13L);
		assertThat(result.participantCount()).isEqualTo(9L);
		assertThat(result.failureCode()).isNull();
	}

	@ParameterizedTest
	@EnumSource(BidFailureCode.class)
	@SuppressWarnings("unchecked")
	void mapsEverySpecifiedFailureCode(BidFailureCode failureCode) {
		when(redisTemplate.execute(
			any(RedisScript.class),
			any(List.class),
			any(),
			any(),
			any(),
			any(),
			any()
		)).thenReturn("ERR|" + failureCode.name());

		BidProcessingResult result = processor.process(command("request-2"));

		assertThat(result.accepted()).isFalse();
		assertThat(result.failureCode()).isEqualTo(failureCode);
	}

	@Test
	@SuppressWarnings("unchecked")
	void rejectsMissingOrMalformedRedisResults() {
		when(redisTemplate.execute(
			any(RedisScript.class),
			any(List.class),
			any(),
			any(),
			any(),
			any(),
			any()
		)).thenReturn(null, "OK|broken");

		assertThatThrownBy(() -> processor.process(command("missing")))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("no bid processing result");
		assertThatThrownBy(() -> processor.process(command("malformed")))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("unknown bid processing result");
	}

	private BidCommand command(String requestId) {
		return new BidCommand(100L, 25L, 82_000L, requestId, REQUESTED_AT);
	}
}
