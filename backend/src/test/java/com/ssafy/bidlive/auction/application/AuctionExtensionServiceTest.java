package com.ssafy.bidlive.auction.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import com.ssafy.bidlive.auction.domain.AuctionExtensionErrorCode;
import com.ssafy.bidlive.auction.domain.AuctionExtendedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/** Redis 연장 성공만 PostgreSQL에 반영하고 거부 결과는 변경 없이 전달하는지 검증한다. */
@ExtendWith(MockitoExtension.class)
class AuctionExtensionServiceTest {

	private static final long AUCTION_ID = 100L;
	private static final long SELLER_ID = 25L;
	private static final Instant NOW =
		Instant.parse("2026-07-20T12:20:00Z");
	private static final Instant PREVIOUS_END_AT = NOW.plusSeconds(60);
	private static final Instant END_AT = PREVIOUS_END_AT.plusSeconds(300);

	@Mock
	private AuctionExtensionProcessor extensionProcessor;

	@Mock
	private AuctionExtensionPersistence extensionPersistence;

	@Mock
	private ApplicationEventPublisher eventPublisher;

	private AuctionExtensionService service;

	@BeforeEach
	void setUp() {
		service = new AuctionExtensionService(
			extensionProcessor,
			extensionPersistence,
			eventPublisher,
			Clock.fixed(NOW, ZoneOffset.UTC)
		);
	}

	@Test
	void persistsRedisAcceptedStateAndReturnsIt() {
		when(extensionProcessor.extend(org.mockito.ArgumentMatchers.any()))
			.thenReturn(AuctionExtensionProcessingResult.accepted(
				PREVIOUS_END_AT,
				END_AT,
				2,
				1,
				14
			));

		AuctionExtensionResult result = service.extend(
			AUCTION_ID,
			SELLER_ID
		);

		ArgumentCaptor<AuctionExtensionCommand> commandCaptor =
			ArgumentCaptor.forClass(AuctionExtensionCommand.class);
		verify(extensionProcessor).extend(commandCaptor.capture());
		assertThat(commandCaptor.getValue()).isEqualTo(
			new AuctionExtensionCommand(AUCTION_ID, SELLER_ID, NOW)
		);
		verify(extensionPersistence).synchronizeFinalState(
			AUCTION_ID,
			END_AT,
			2
		);
		verify(eventPublisher).publishEvent(new AuctionExtendedEvent(
			AUCTION_ID,
			END_AT,
			2,
			1
		));
		assertThat(result).isEqualTo(new AuctionExtensionResult(
			AUCTION_ID,
			PREVIOUS_END_AT,
			END_AT,
			2,
			1
		));
	}

	@ParameterizedTest
	@EnumSource(AuctionExtensionErrorCode.class)
	void doesNotTouchPostgresWhenRedisRejects(
		AuctionExtensionErrorCode failureCode
	) {
		when(extensionProcessor.extend(org.mockito.ArgumentMatchers.any()))
			.thenReturn(AuctionExtensionProcessingResult.rejected(
				failureCode
			));

		assertThatThrownBy(() -> service.extend(AUCTION_ID, SELLER_ID))
			.isInstanceOf(AuctionExtensionException.class)
			.satisfies(exception -> assertThat(
				((AuctionExtensionException) exception).getReason()
			).isEqualTo(failureCode));
		verify(extensionPersistence, never()).synchronizeFinalState(
			org.mockito.ArgumentMatchers.anyLong(),
			org.mockito.ArgumentMatchers.any(),
			org.mockito.ArgumentMatchers.anyInt()
		);
		verify(eventPublisher, never()).publishEvent(
			org.mockito.ArgumentMatchers.any(AuctionExtendedEvent.class)
		);
	}
}
