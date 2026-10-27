package com.ssafy.bidlive.auction.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import com.ssafy.bidlive.auction.domain.AuctionRoomDetailErrorCode;
import com.ssafy.bidlive.auction.domain.AuctionStatus;
import com.ssafy.bidlive.media.application.ViewerCountQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 공개·삭제 정책과 판매자·관리자 전용 필드 및 서버 시각 계산을 검증합니다. */
class AuctionRoomDetailQueryServiceTest {

	private static final Instant NOW = Instant.parse("2026-07-21T10:59:30Z");

	private AuctionRoomDetailPersistence persistence;
	private ViewerCountQueryService viewerCountQueryService;
	private AuctionRoomDetailQueryService service;

	@BeforeEach
	void setUp() {
		persistence = mock(AuctionRoomDetailPersistence.class);
		viewerCountQueryService = mock(ViewerCountQueryService.class);
		service = new AuctionRoomDetailQueryService(
			persistence,
			viewerCountQueryService,
			Clock.fixed(NOW, ZoneOffset.UTC)
		);
		when(viewerCountQueryService.activeViewerCount(100L)).thenReturn(18L);
	}

	@Test
	void returnsPublicDetailAndHidesPrivilegedFieldsFromAnonymousViewer() {
		when(persistence.findByAuctionId(100L)).thenReturn(Optional.of(room()));

		AuctionRoomDetailResult result = service.getRoom(
			100L,
			AuctionRoomViewerContext.anonymous()
		);

		assertThat(result.serverTime()).isEqualTo(NOW);
		assertThat(result.viewerCount()).isEqualTo(18);
		assertThat(result.seller()).isFalse();
		assertThat(result.notificationCount()).isNull();
		assertThat(result.sellerInfo().nickname()).isEqualTo("판**");
		assertThat(result.sellerInfo().rating()).isNull();
		assertThat(result.canEnter()).isTrue();
	}

	@Test
	void exposesNotificationCountToSellerAndAdmin() {
		when(persistence.findByAuctionId(100L)).thenReturn(Optional.of(room()));

		AuctionRoomDetailResult sellerResult = service.getRoom(
			100L,
			AuctionRoomViewerContext.authenticated(25L, false)
		);
		AuctionRoomDetailResult adminResult = service.getRoom(
			100L,
			AuctionRoomViewerContext.authenticated(99L, true)
		);

		assertThat(sellerResult.seller()).isTrue();
		assertThat(sellerResult.notificationCount()).isEqualTo(12);
		assertThat(adminResult.seller()).isFalse();
		assertThat(adminResult.notificationCount()).isEqualTo(12);
	}

	@Test
	void preventsEntryAfterAuctionEnds() {
		when(persistence.findByAuctionId(100L)).thenReturn(
			Optional.of(room(AuctionStatus.ENDED, "PUBLIC", null, "PENDING"))
		);

		assertThat(service.getRoom(100L, AuctionRoomViewerContext.anonymous())
			.canEnter()).isFalse();
	}

	@Test
	void rejectsInvalidOrMissingAuctionIdBeforeExternalCounts() {
		assertThatThrownBy(() -> service.getRoom(
			0L,
			AuctionRoomViewerContext.anonymous()
		)).satisfies(exception -> assertErrorCode(
			exception,
			AuctionRoomDetailErrorCode.INVALID_AUCTION_ID
		));

		when(persistence.findByAuctionId(404L)).thenReturn(Optional.empty());
		assertThatThrownBy(() -> service.getRoom(
			404L,
			AuctionRoomViewerContext.anonymous()
		)).satisfies(exception -> assertErrorCode(
			exception,
			AuctionRoomDetailErrorCode.AUCTION_NOT_FOUND
		));
		verify(viewerCountQueryService, never()).activeViewerCount(404L);
	}

	@Test
	void distinguishesDeletedRoomFromUnavailablePublicRoom() {
		when(persistence.findByAuctionId(100L)).thenReturn(Optional.of(
			room(AuctionStatus.WAITING, "PUBLIC", NOW, "PENDING")
		));
		assertThatThrownBy(() -> service.getRoom(
			100L,
			AuctionRoomViewerContext.anonymous()
		)).satisfies(exception -> assertErrorCode(
			exception,
			AuctionRoomDetailErrorCode.AUCTION_DELETED
		));

		when(persistence.findByAuctionId(101L)).thenReturn(Optional.of(
			room(AuctionStatus.WAITING, "PRIVATE", null, "PENDING")
		));
		assertThatThrownBy(() -> service.getRoom(
			101L,
			AuctionRoomViewerContext.anonymous()
		)).satisfies(exception -> assertErrorCode(
			exception,
			AuctionRoomDetailErrorCode.AUCTION_NOT_FOUND
		));
	}

	@Test
	void hidesBlockedProductAsNotFound() {
		when(persistence.findByAuctionId(100L)).thenReturn(Optional.of(
			room(AuctionStatus.WAITING, "PUBLIC", null, "BLOCKED")
		));

		assertThatThrownBy(() -> service.getRoom(
			100L,
			AuctionRoomViewerContext.anonymous()
		)).satisfies(exception -> assertErrorCode(
			exception,
			AuctionRoomDetailErrorCode.AUCTION_NOT_FOUND
		));
	}

	private static AuctionRoomDetailRow room() {
		return room(AuctionStatus.WAITING, "PUBLIC", null, "PENDING");
	}

	private static AuctionRoomDetailRow room(
		AuctionStatus status,
		String visibility,
		Instant deletedAt,
		String moderationStatus
	) {
		return new AuctionRoomDetailRow(
			100L,
			25L,
			"키크론 Q1 Pro 경매",
			status,
			50_000L,
			Instant.parse("2026-07-21T11:00:00Z"),
			20,
			visibility,
			deletedAt,
			501L,
			"키크론 Q1 Pro",
			"상품 설명",
			"ELECTRONICS",
			"https://cdn.example.com/products/501.png",
			moderationStatus,
			"판매왕",
			12
		);
	}

	private static void assertErrorCode(
		Throwable exception,
		AuctionRoomDetailErrorCode expected
	) {
		assertThat(exception).isInstanceOf(AuctionRoomDetailException.class);
		assertThat(((AuctionRoomDetailException) exception).getErrorCode())
			.isEqualTo(expected);
	}
}
