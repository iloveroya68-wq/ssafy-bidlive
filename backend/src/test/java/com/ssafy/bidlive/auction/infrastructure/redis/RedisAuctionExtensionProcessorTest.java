package com.ssafy.bidlive.auction.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import com.ssafy.bidlive.auction.application.AuctionExtensionCommand;
import com.ssafy.bidlive.auction.application.AuctionExtensionProcessingResult;
import com.ssafy.bidlive.auction.domain.AuctionExtensionErrorCode;
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

/** Redis 연장 Lua 호출 인자와 성공·거부·비정상 결과 변환을 검증한다. */
@ExtendWith(MockitoExtension.class)
class RedisAuctionExtensionProcessorTest {

	private static final Instant REQUESTED_AT =
		Instant.parse("2026-07-20T12:20:00Z");

	@Mock
	private StringRedisTemplate redisTemplate;

	private RedisAuctionExtensionProcessor processor;

	@BeforeEach
	void setUp() {
		processor = new RedisAuctionExtensionProcessor(redisTemplate);
	}

	@Test
	@SuppressWarnings("unchecked")
	void sendsOneStateKeyAndMapsAcceptedResult() {
		when(redisTemplate.execute(
			any(RedisScript.class),
			any(List.class),
			eq("25"),
			eq(Long.toString(REQUESTED_AT.toEpochMilli()))
		)).thenReturn("OK|1784540460000|1784540760000|2|1|14");

		AuctionExtensionProcessingResult result = processor.extend(command());

		ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.forClass(
			List.class
		);
		verify(redisTemplate).execute(
			any(RedisScript.class),
			keysCaptor.capture(),
			eq("25"),
			eq(Long.toString(REQUESTED_AT.toEpochMilli()))
		);
		assertThat(keysCaptor.getValue()).containsExactly(
			"bid:{bid}:auction:100:state"
		);
		assertThat(result.accepted()).isTrue();
		assertThat(result.previousEndAt()).isEqualTo(
			Instant.ofEpochMilli(1_784_540_460_000L)
		);
		assertThat(result.endAt()).isEqualTo(
			Instant.ofEpochMilli(1_784_540_760_000L)
		);
		assertThat(result.extensionCount()).isEqualTo(2);
		assertThat(result.remainingExtensions()).isEqualTo(1);
		assertThat(result.version()).isEqualTo(14);
	}

	@ParameterizedTest
	@EnumSource(AuctionExtensionErrorCode.class)
	@SuppressWarnings("unchecked")
	void mapsEverySpecifiedFailure(
		AuctionExtensionErrorCode failureCode
	) {
		when(redisTemplate.execute(
			any(RedisScript.class),
			any(List.class),
			any(),
			any()
		)).thenReturn("ERR|" + failureCode.name());

		AuctionExtensionProcessingResult result = processor.extend(command());

		assertThat(result.accepted()).isFalse();
		assertThat(result.failureCode()).isEqualTo(failureCode);
	}

	@Test
	@SuppressWarnings("unchecked")
	void rejectsMissingMalformedAndInvalidStateResults() {
		when(redisTemplate.execute(
			any(RedisScript.class),
			any(List.class),
			any(),
			any()
		)).thenReturn(null, "OK|broken", "SYS|INVALID_AUCTION_STATE");

		assertThatThrownBy(() -> processor.extend(command()))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("unknown auction extension result");
		assertThatThrownBy(() -> processor.extend(command()))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("unknown auction extension result");
		assertThatThrownBy(() -> processor.extend(command()))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("INVALID_AUCTION_STATE");
	}

	private static AuctionExtensionCommand command() {
		return new AuctionExtensionCommand(100L, 25L, REQUESTED_AT);
	}
}
