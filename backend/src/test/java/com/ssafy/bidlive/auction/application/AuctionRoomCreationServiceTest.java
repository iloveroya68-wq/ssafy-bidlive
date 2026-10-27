package com.ssafy.bidlive.auction.application;

import static com.ssafy.bidlive.auction.domain.AuctionRoomCreationErrorCode.INVALID_AUCTION_REQUEST;
import static com.ssafy.bidlive.auction.domain.AuctionRoomCreationErrorCode.INVALID_PRODUCT_REQUEST;
import static com.ssafy.bidlive.auction.domain.AuctionRoomCreationErrorCode.INVALID_SCHEDULE_TIME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import com.ssafy.bidlive.auction.domain.AuctionStartType;
import com.ssafy.bidlive.auction.domain.AuctionStatus;
import com.ssafy.bidlive.member.application.MemberPersistence;
import com.ssafy.bidlive.member.domain.Member;
import com.ssafy.bidlive.product.application.ProductRegistrationCommand;
import com.ssafy.bidlive.product.application.ProductRegistrationException;
import com.ssafy.bidlive.product.application.ProductRegistrationResult;
import com.ssafy.bidlive.product.application.ProductRegistrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuctionRoomCreationServiceTest {

	private static final Instant NOW = Instant.parse("2026-07-29T09:00:00Z");

	@Mock
	private MemberPersistence memberPersistence;

	@Mock
	private ProductRegistrationService productRegistrationService;

	@Mock
	private AuctionRoomCreationPersistence persistence;

	private AuctionRoomCreationService service;

	@BeforeEach
	void setUp() {
		service = new AuctionRoomCreationService(
			memberPersistence,
			productRegistrationService,
			persistence,
			Clock.fixed(NOW, ZoneOffset.UTC)
		);
	}

	@Test
	void createsWaitingScheduledAuctionRoom() {
		Member member = Member.createOAuthMember();
		AuctionRoomCreationCommand command = scheduledCommand(
			"2026-07-29T19:00:00+09:00");
		when(memberPersistence.findById(25L)).thenReturn(Optional.of(member));
		when(productRegistrationService.register(command.product()))
			.thenReturn(new ProductRegistrationResult(501L));
		when(persistence.save(
			501L,
			50_000L,
			15,
			AuctionStartType.SCHEDULED,
			java.time.OffsetDateTime.parse("2026-07-29T19:00:00+09:00"),
			AuctionStatus.WAITING,
			java.time.OffsetDateTime.parse("2026-07-29T09:00:00Z")
		)).thenReturn(100L);

		AuctionRoomCreationResult result = service.create(command);

		assertThat(result.auctionId()).isEqualTo(100L);
		assertThat(result.productId()).isEqualTo(501L);
		assertThat(result.status()).isEqualTo(AuctionStatus.WAITING);
		assertThat(result.startType()).isEqualTo(AuctionStartType.SCHEDULED);
	}

	@Test
	void rejectsImmediateAuctionWithScheduledTime() {
		AuctionRoomCreationCommand command = new AuctionRoomCreationCommand(
			25L,
			productCommand(),
			50_000L,
			15,
			"IMMEDIATE",
			"2026-07-29T19:00:00+09:00"
		);
		when(memberPersistence.findById(25L))
			.thenReturn(Optional.of(Member.createOAuthMember()));

		assertThatThrownBy(() -> service.create(command))
			.isInstanceOfSatisfying(
				AuctionRoomCreationException.class,
				exception -> assertThat(exception.getErrorCode())
					.isEqualTo(INVALID_AUCTION_REQUEST)
			);
		verify(productRegistrationService, never()).register(command.product());
	}

	@Test
	void rejectsPastScheduledTime() {
		AuctionRoomCreationCommand command = scheduledCommand(
			"2026-07-29T17:59:59+09:00");
		when(memberPersistence.findById(25L))
			.thenReturn(Optional.of(Member.createOAuthMember()));

		assertThatThrownBy(() -> service.create(command))
			.isInstanceOfSatisfying(
				AuctionRoomCreationException.class,
				exception -> assertThat(exception.getErrorCode())
					.isEqualTo(INVALID_SCHEDULE_TIME)
			);
		verify(productRegistrationService, never()).register(command.product());
	}

	@Test
	void mapsInvalidOwnedImageToProductRequestError() {
		AuctionRoomCreationCommand command = scheduledCommand(
			"2026-07-29T19:00:00+09:00");
		when(memberPersistence.findById(25L))
			.thenReturn(Optional.of(Member.createOAuthMember()));
		when(productRegistrationService.register(command.product()))
			.thenThrow(new ProductRegistrationException());

		assertThatThrownBy(() -> service.create(command))
			.isInstanceOfSatisfying(
				AuctionRoomCreationException.class,
				exception -> assertThat(exception.getErrorCode())
					.isEqualTo(INVALID_PRODUCT_REQUEST)
			);
		verify(persistence, never()).save(
			org.mockito.ArgumentMatchers.any(),
			org.mockito.ArgumentMatchers.anyLong(),
			org.mockito.ArgumentMatchers.anyInt(),
			org.mockito.ArgumentMatchers.any(),
			org.mockito.ArgumentMatchers.any(),
			org.mockito.ArgumentMatchers.any(),
			org.mockito.ArgumentMatchers.any()
		);
	}

	private AuctionRoomCreationCommand scheduledCommand(String scheduledAt) {
		return new AuctionRoomCreationCommand(
			25L,
			productCommand(),
			50_000L,
			15,
			"SCHEDULED",
			scheduledAt
		);
	}

	private ProductRegistrationCommand productCommand() {
		return new ProductRegistrationCommand(
			25L,
			"키크론 Q1 Pro",
			"구성품 포함",
			"ELECTRONICS",
			"https://cdn.example.com/products/image.jpg"
		);
	}
}
