package com.ssafy.bidlive.auction.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import com.ssafy.bidlive.auction.domain.AuctionRoomStartErrorCode;
import com.ssafy.bidlive.auction.domain.AuctionStartedEvent;
import com.ssafy.bidlive.auction.domain.AuctionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

/** 시작 판정 순서, 서버 기준 종료 시각 계산, 중복 시작 경합, 발행 payload를 검증한다. */
class AuctionRoomStartServiceTest {

	private static final Instant NOW = Instant.parse("2026-07-21T11:02:13Z");
	private static final long AUCTION_ID = 100L;
	private static final long SELLER_ID = 25L;

	private AuctionRoomStartPersistence persistence;
	private ApplicationEventPublisher eventPublisher;
	private AuctionRoomStartService service;

	@BeforeEach
	void setUp() {
		persistence = mock(AuctionRoomStartPersistence.class);
		eventPublisher = mock(ApplicationEventPublisher.class);
		service = new AuctionRoomStartService(
			persistence,
			eventPublisher,
			Clock.fixed(NOW, ZoneOffset.UTC)
		);
		when(persistence.markStarted(anyLong(), any(), any())).thenReturn(1);
		givenTarget(target("WAITING", "PUBLIC", false, "PASSED"));
	}

	/** 종료 시각은 `startedAt + durationMinutes`를 **서버가** 계산한다 */
	@Test
	void startsRoomAndComputesEndAtFromDuration() {
		AuctionRoomStartResult result = service.startRoom(AUCTION_ID, SELLER_ID);

		assertThat(result.status()).isEqualTo(AuctionStatus.LIVE);
		assertThat(result.startedAt()).isEqualTo(NOW);
		assertThat(result.endAt()).isEqualTo(NOW.plusSeconds(15 * 60));
		assertThat(result.durationMinutes()).isEqualTo(15);
		assertThat(result.serverTime()).isEqualTo(NOW);
		verify(persistence).markStarted(AUCTION_ID, NOW, NOW.plusSeconds(15 * 60));
	}

	/** 입찰 상태 초기화와 브로드캐스트가 쓸 값이 payload에 다 들어가야 한다 */
	@Test
	void publishesEventWithValuesSubscribersNeed() {
		service.startRoom(AUCTION_ID, SELLER_ID);

		ArgumentCaptor<AuctionStartedEvent> captor =
			ArgumentCaptor.forClass(AuctionStartedEvent.class);
		verify(eventPublisher).publishEvent(captor.capture());
		AuctionStartedEvent event = captor.getValue();
		assertThat(event.auctionId()).isEqualTo(AUCTION_ID);
		assertThat(event.sellerId()).isEqualTo(SELLER_ID);
		assertThat(event.reservePrice()).isEqualTo(50_000L);
		assertThat(event.startedAt()).isEqualTo(NOW);
		assertThat(event.endAt()).isEqualTo(NOW.plusSeconds(15 * 60));
	}

	@Test
	void rejectsUnauthenticatedRequest() {
		assertThatThrownBy(() -> service.startRoom(AUCTION_ID, null))
			.satisfies(e -> assertThat(codeOf(e))
				.isEqualTo(AuctionRoomStartErrorCode.UNAUTHORIZED));
		verify(persistence, never()).findStartTarget(anyLong());
	}

	@Test
	void rejectsMissingOrDeletedRoom() {
		when(persistence.findStartTarget(AUCTION_ID)).thenReturn(Optional.empty());
		assertThatThrownBy(() -> service.startRoom(AUCTION_ID, SELLER_ID))
			.satisfies(e -> assertThat(codeOf(e))
				.isEqualTo(AuctionRoomStartErrorCode.AUCTION_NOT_FOUND));

		givenTarget(target("WAITING", "PUBLIC", true, "PASSED"));
		assertThatThrownBy(() -> service.startRoom(AUCTION_ID, SELLER_ID))
			.satisfies(e -> assertThat(codeOf(e))
				.isEqualTo(AuctionRoomStartErrorCode.AUCTION_NOT_FOUND));
	}

	/** 비공개 방은 타인에게 404로 숨긴다 (S15P11A704-482과 같은 태도) */
	@Test
	void hidesPrivateRoomFromNonSellerWithNotFound() {
		givenTarget(target("WAITING", "PRIVATE", false, "PASSED"));

		assertThatThrownBy(() -> service.startRoom(AUCTION_ID, 999L))
			.satisfies(e -> assertThat(codeOf(e))
				.isEqualTo(AuctionRoomStartErrorCode.AUCTION_NOT_FOUND));
	}

	@Test
	void allowsSellerToStartOwnPrivateRoom() {
		givenTarget(target("WAITING", "PRIVATE", false, "PASSED"));

		assertThat(service.startRoom(AUCTION_ID, SELLER_ID).status())
			.isEqualTo(AuctionStatus.LIVE);
	}

	@Test
	void rejectsMemberWhoIsNotTheSeller() {
		assertThatThrownBy(() -> service.startRoom(AUCTION_ID, 999L))
			.satisfies(e -> assertThat(codeOf(e))
				.isEqualTo(AuctionRoomStartErrorCode.NOT_AUCTION_SELLER));
	}

	@Test
	void rejectsRoomThatIsAlreadyLive() {
		givenTarget(target("LIVE", "PUBLIC", false, "PASSED"));

		assertThatThrownBy(() -> service.startRoom(AUCTION_ID, SELLER_ID))
			.satisfies(e -> assertThat(codeOf(e))
				.isEqualTo(AuctionRoomStartErrorCode.AUCTION_ALREADY_STARTED));
		verify(eventPublisher, never()).publishEvent(any(AuctionStartedEvent.class));
	}

	@Test
	void rejectsEndedOrUnknownStatusAsNotAllowed() {
		for (String status : new String[] {"ENDED", "CANCELLED"}) {
			givenTarget(target(status, "PUBLIC", false, "PASSED"));

			assertThatThrownBy(() -> service.startRoom(AUCTION_ID, SELLER_ID))
				.satisfies(e -> assertThat(codeOf(e))
					.isEqualTo(AuctionRoomStartErrorCode.AUCTION_START_NOT_ALLOWED));
		}
	}

	/** 확정 차단만 막는다 — `PENDING`은 판정 전, `FLAGGED`는 AI 미확정이라 시작을 못 막는다 */
	@Test
	void blocksOnlyConfirmedModerationBlock() {
		givenTarget(target("WAITING", "PUBLIC", false, "BLOCKED"));
		assertThatThrownBy(() -> service.startRoom(AUCTION_ID, SELLER_ID))
			.satisfies(e -> assertThat(codeOf(e))
				.isEqualTo(AuctionRoomStartErrorCode.AUCTION_MODERATION_BLOCKED));

		for (String moderation : new String[] {"PENDING", "FLAGGED", "PASSED"}) {
			givenTarget(target("WAITING", "PUBLIC", false, moderation));
			assertThat(service.startRoom(AUCTION_ID, SELLER_ID).status())
				.isEqualTo(AuctionStatus.LIVE);
		}
	}

	/**
	 * 판정과 전이 사이에 다른 요청이 먼저 시작한 경우. 조건부 UPDATE가 0행을 반환하고,
	 * 명세의 "중복 시작 요청은 두 번 처리하지 않는다"가 여기서 지켜진다 —
	 * **이벤트도 발행하지 않아야 한다.**
	 */
	@Test
	void rejectsConcurrentSecondStartWithoutPublishingEvent() {
		when(persistence.markStarted(anyLong(), any(), any())).thenReturn(0);

		assertThatThrownBy(() -> service.startRoom(AUCTION_ID, SELLER_ID))
			.satisfies(e -> assertThat(codeOf(e))
				.isEqualTo(AuctionRoomStartErrorCode.AUCTION_ALREADY_STARTED));
		verify(eventPublisher, never()).publishEvent(any(AuctionStartedEvent.class));
	}

	private void givenTarget(AuctionRoomStartTarget target) {
		when(persistence.findStartTarget(AUCTION_ID)).thenReturn(Optional.of(target));
	}

	private static AuctionRoomStartTarget target(
		String status,
		String visibility,
		boolean deleted,
		String moderationStatus
	) {
		return new AuctionRoomStartTarget(
			AUCTION_ID, SELLER_ID, status, visibility, deleted, 50_000L, 15, moderationStatus
		);
	}

	private static AuctionRoomStartErrorCode codeOf(Throwable exception) {
		return ((AuctionRoomStartException) exception).getErrorCode();
	}
}
