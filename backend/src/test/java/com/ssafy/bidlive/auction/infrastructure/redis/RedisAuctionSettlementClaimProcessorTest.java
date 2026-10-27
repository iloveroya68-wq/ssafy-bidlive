package com.ssafy.bidlive.auction.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import com.ssafy.bidlive.auction.application.AuctionSettlementClaimCommand;
import com.ssafy.bidlive.auction.application.AuctionSettlementClaimResult;
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

/** Redis 정산 선점 Lua 호출 인자와 성공·거부·비정상 결과 변환을 검증한다. */
@ExtendWith(MockitoExtension.class)
class RedisAuctionSettlementClaimProcessorTest {

	private static final Instant REQUESTED_AT =
		Instant.parse("2026-07-20T12:20:00Z");

	@Mock
	private StringRedisTemplate redisTemplate;

	private RedisAuctionSettlementClaimProcessor processor;

	@BeforeEach
	void setUp() {
		processor = new RedisAuctionSettlementClaimProcessor(redisTemplate);
	}

	@Test
	@SuppressWarnings("unchecked")
	void sendsStateKeyAndServerTimeAndMapsClaimedResult() {
		when(redisTemplate.execute(
			any(RedisScript.class),
			any(List.class),
			eq(Long.toString(REQUESTED_AT.toEpochMilli())),
			eq(REQUESTED_AT.toString())
		)).thenReturn("OK|CLAIMED");

		AuctionSettlementClaimResult result = processor.claim(command());

		ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.forClass(
			List.class
		);
		verify(redisTemplate).execute(
			any(RedisScript.class),
			keysCaptor.capture(),
			eq(Long.toString(REQUESTED_AT.toEpochMilli())),
			eq(REQUESTED_AT.toString())
		);
		assertThat(keysCaptor.getValue()).containsExactly(
			"bid:{bid}:auction:100:state"
		);
		assertThat(result).isEqualTo(AuctionSettlementClaimResult.CLAIMED);
		assertThat(result.claimed()).isTrue();
	}

	@ParameterizedTest
	@EnumSource(
		value = AuctionSettlementClaimResult.class,
		names = "CLAIMED",
		mode = EnumSource.Mode.EXCLUDE
	)
	@SuppressWarnings("unchecked")
	void mapsEveryNonClaimedResult(
		AuctionSettlementClaimResult expected
	) {
		when(redisTemplate.execute(
			any(RedisScript.class),
			any(List.class),
			any(),
			any()
		)).thenReturn("ERR|" + expected.name());

		AuctionSettlementClaimResult result = processor.claim(command());

		assertThat(result).isEqualTo(expected);
		assertThat(result.claimed()).isFalse();
	}

	@Test
	@SuppressWarnings("unchecked")
	void rejectsMissingMalformedAndInvalidStateResults() {
		when(redisTemplate.execute(
			any(RedisScript.class),
			any(List.class),
			any(),
			any()
		)).thenReturn(
			null,
			"OK|ALREADY_ENDED",
			"ERR|CLAIMED",
			"SYS|INVALID_AUCTION_STATE"
		);

		assertThatThrownBy(() -> processor.claim(command()))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("unknown auction settlement claim result");
		assertThatThrownBy(() -> processor.claim(command()))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("invalid auction settlement claim result");
		assertThatThrownBy(() -> processor.claim(command()))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("invalid auction settlement claim result");
		assertThatThrownBy(() -> processor.claim(command()))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("INVALID_AUCTION_STATE");
	}

	private static AuctionSettlementClaimCommand command() {
		return new AuctionSettlementClaimCommand(100L, REQUESTED_AT);
	}
}
