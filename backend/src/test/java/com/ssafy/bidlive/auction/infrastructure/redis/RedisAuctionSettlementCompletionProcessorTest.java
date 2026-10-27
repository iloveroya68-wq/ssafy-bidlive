package com.ssafy.bidlive.auction.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import com.ssafy.bidlive.auction.application.AuctionSettlementCompletionCommand;
import com.ssafy.bidlive.auction.application.AuctionSettlementCompletionResult;
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

/** Redis 정산 완료 Lua 호출 인자와 상태별 결과 변환을 검증한다. */
@ExtendWith(MockitoExtension.class)
class RedisAuctionSettlementCompletionProcessorTest {

	private static final Instant COMPLETED_AT =
		Instant.parse("2026-07-20T12:20:01Z");

	@Mock
	private StringRedisTemplate redisTemplate;

	private RedisAuctionSettlementCompletionProcessor processor;

	@BeforeEach
	void setUp() {
		processor = new RedisAuctionSettlementCompletionProcessor(
			redisTemplate
		);
	}

	@Test
	@SuppressWarnings("unchecked")
	void sendsStateKeyAndCompletionTime() {
		when(redisTemplate.execute(
			any(RedisScript.class),
			any(List.class),
			eq(COMPLETED_AT.toString())
		)).thenReturn("OK|COMPLETED");

		AuctionSettlementCompletionResult result = processor.complete(
			command()
		);

		ArgumentCaptor<List<String>> keys = ArgumentCaptor.forClass(
			List.class
		);
		verify(redisTemplate).execute(
			any(RedisScript.class),
			keys.capture(),
			eq(COMPLETED_AT.toString())
		);
		assertThat(keys.getValue()).containsExactly(
			"bid:{bid}:auction:100:state"
		);
		assertThat(result).isEqualTo(
			AuctionSettlementCompletionResult.COMPLETED
		);
	}

	@ParameterizedTest
	@EnumSource(AuctionSettlementCompletionResult.class)
	@SuppressWarnings("unchecked")
	void mapsEveryKnownResult(
		AuctionSettlementCompletionResult expected
	) {
		String prefix = expected.completed() ? "OK|" : "ERR|";
		when(redisTemplate.execute(
			any(RedisScript.class),
			any(List.class),
			any()
		)).thenReturn(prefix + expected.name());

		assertThat(processor.complete(command())).isEqualTo(expected);
	}

	@Test
	@SuppressWarnings("unchecked")
	void rejectsMalformedOrMismatchedResults() {
		when(redisTemplate.execute(
			any(RedisScript.class),
			any(List.class),
			any()
		)).thenReturn(null, "ERR|COMPLETED", "OK|AUCTION_NOT_FOUND");

		assertThatThrownBy(() -> processor.complete(command()))
			.isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> processor.complete(command()))
			.isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> processor.complete(command()))
			.isInstanceOf(IllegalStateException.class);
	}

	private AuctionSettlementCompletionCommand command() {
		return new AuctionSettlementCompletionCommand(100L, COMPLETED_AT);
	}
}
