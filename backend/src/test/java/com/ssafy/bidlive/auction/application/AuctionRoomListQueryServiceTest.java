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
import java.util.List;

import com.ssafy.bidlive.auction.domain.AuctionRoomListCursor;
import com.ssafy.bidlive.auction.domain.AuctionRoomListErrorCode;
import com.ssafy.bidlive.auction.domain.AuctionStatus;
import com.ssafy.bidlive.media.application.ViewerCountQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** 조회 조건 검증, 크기 제한, 판매자 닉네임 공개 규칙과 serverTime 생성을 검증한다. */
class AuctionRoomListQueryServiceTest {

	private static final Instant NOW = Instant.parse("2026-07-21T10:59:30Z");

	private AuctionRoomListPersistence persistence;
	private ViewerCountQueryService viewerCountQueryService;
	private AuctionRoomListQueryService service;

	@BeforeEach
	void setUp() {
		persistence = mock(AuctionRoomListPersistence.class);
		viewerCountQueryService = mock(ViewerCountQueryService.class);
		service = new AuctionRoomListQueryService(
			persistence,
			viewerCountQueryService,
			Clock.fixed(NOW, ZoneOffset.UTC)
		);
		when(persistence.existsCategory(anyLong())).thenReturn(true);
		when(persistence.findRooms(any()))
			.thenReturn(new AuctionRoomListSlice(List.of(), null, false));
		when(persistence.countByStatus(any()))
			.thenReturn(new AuctionRoomStatusCounts(0L, 0L));
	}

	@Test
	void rejectsUnsupportedStatusFilter() {
		assertThatThrownBy(() -> service.getRooms("PAUSED", null, null, null, null))
			.isInstanceOf(AuctionRoomListException.class)
			.satisfies(exception -> assertThat(errorCodeOf(exception))
				.isEqualTo(AuctionRoomListErrorCode.INVALID_AUCTION_STATUS));
	}

	/**
	 * 정렬 키가 시각이 아니면 400이어야 한다. 커서 쪽에서 안 끊으면 조회 쿼리의 `TIMESTAMPTZ`
	 * 캐스팅까지 내려가 500이 나간다 — 잘못된 요청을 서버 장애로 보고하는 셈이다.
	 */
	@Test
	void rejectsCursorWhoseSortValueIsNotATimestamp() {
		String cursor = java.util.Base64.getUrlEncoder()
			.withoutPadding()
			.encodeToString("LIVE|아무거나|1".getBytes(java.nio.charset.StandardCharsets.UTF_8));

		assertThatThrownBy(() -> service.getRooms("LIVE", null, null, cursor, null))
			.isInstanceOf(AuctionRoomListException.class)
			.satisfies(exception -> assertThat(errorCodeOf(exception))
				.isEqualTo(AuctionRoomListErrorCode.INVALID_CURSOR));
	}

	@Test
	void rejectsMalformedCursor() {
		assertThatThrownBy(() -> service.getRooms(null, null, null, "not*base64*", null))
			.isInstanceOf(AuctionRoomListException.class)
			.satisfies(exception -> assertThat(errorCodeOf(exception))
				.isEqualTo(AuctionRoomListErrorCode.INVALID_CURSOR));
	}

	/**
	 * 정렬 기준이 상태마다 다르므로 커서를 다른 탭에 쓰면 `LIVE`의 `ends_at` 값이 `ENDED`의
	 * `ended_at` 내림차순 비교에 들어가 **방이 조용히 누락된다.** 400으로 끊는 게 유일한 안전장치다.
	 */
	@Test
	void rejectsCursorIssuedUnderADifferentStatusFilter() {
		String liveCursor =
			new AuctionRoomListCursor(AuctionStatus.LIVE, "2026-07-21T11:15:00Z", 100L).encode();

		assertThatThrownBy(() -> service.getRooms("ENDED", null, null, liveCursor, null))
			.isInstanceOf(AuctionRoomListException.class)
			.satisfies(exception -> assertThat(errorCodeOf(exception))
				.isEqualTo(AuctionRoomListErrorCode.INVALID_CURSOR));
		assertThatThrownBy(() -> service.getRooms("WAITING", null, null, liveCursor, null))
			.isInstanceOf(AuctionRoomListException.class)
			.satisfies(exception -> assertThat(errorCodeOf(exception))
				.isEqualTo(AuctionRoomListErrorCode.INVALID_CURSOR));
		assertThatThrownBy(() -> service.getRooms(null, null, null, liveCursor, null))
			.isInstanceOf(AuctionRoomListException.class)
			.satisfies(exception -> assertThat(errorCodeOf(exception))
				.isEqualTo(AuctionRoomListErrorCode.INVALID_CURSOR));
	}

	@Test
	void acceptsCursorIssuedUnderTheSameStatusFilter() {
		String liveCursor =
			new AuctionRoomListCursor(AuctionStatus.LIVE, "2026-07-21T11:15:00Z", 100L).encode();

		assertThat(service.getRooms("LIVE", null, null, liveCursor, null).items()).isEmpty();
	}

	@Test
	void rejectsUnknownCategoryBeforeQueryingRooms() {
		when(persistence.existsCategory(999L)).thenReturn(false);

		assertThatThrownBy(() -> service.getRooms(null, 999L, null, null, null))
			.isInstanceOf(AuctionRoomListException.class)
			.satisfies(exception -> assertThat(errorCodeOf(exception))
				.isEqualTo(AuctionRoomListErrorCode.CATEGORY_NOT_FOUND));
		verify(persistence, never()).findRooms(any());
	}

	@Test
	void doesNotCheckCategoryWhenFilterIsAbsent() {
		service.getRooms(null, null, null, null, null);

		verify(persistence, never()).existsCategory(anyLong());
	}

	@Test
	void appliesDefaultAndMaximumSize() {
		assertThat(capturedCriteria(null, null).size()).isEqualTo(8);
		assertThat(capturedCriteria(null, 20).size()).isEqualTo(20);
		assertThat(capturedCriteria(null, 100).size()).isEqualTo(20);
		assertThat(capturedCriteria(null, 0).size()).isEqualTo(1);
	}

	@Test
	void normalizesBlankKeywordAndStatusToNoFilter() {
		AuctionRoomListCriteria criteria = capturedCriteria("  ", null, " ");

		assertThat(criteria.status()).isNull();
		assertThat(criteria.keyword()).isNull();
	}

	@Test
	void trimsAndUppercasesStatusFilter() {
		assertThat(capturedCriteria(" live ", null).status()).isEqualTo(AuctionStatus.LIVE);
	}

	@Test
	void masksSellerNicknameAndStampsServerTimeAfterQuerying() {
		when(persistence.findRooms(any())).thenReturn(new AuctionRoomListSlice(
			List.of(room("서연123")),
			"next",
			true
		));
		when(persistence.countByStatus(any()))
			.thenReturn(new AuctionRoomStatusCounts(3L, 2L));

		AuctionRoomListResult result = service.getRooms("LIVE", null, null, null, null);

		assertThat(result.serverTime()).isEqualTo(NOW);
		assertThat(result.items()).singleElement()
			.satisfies(item -> assertThat(item.sellerNickname()).isEqualTo("서연***"));
		assertThat(result.nextCursor()).isEqualTo("next");
		assertThat(result.hasNext()).isTrue();
		assertThat(result.counts().live()).isEqualTo(3L);
		assertThat(result.counts().waiting()).isEqualTo(2L);
	}

	private AuctionRoomListCriteria capturedCriteria(String status, Integer size) {
		return capturedCriteria(status, size, null);
	}

	private AuctionRoomListCriteria capturedCriteria(String status, Integer size, String keyword) {
		service.getRooms(status, null, keyword, null, size);
		ArgumentCaptor<AuctionRoomListCriteria> captor =
			ArgumentCaptor.forClass(AuctionRoomListCriteria.class);
		verify(persistence, org.mockito.Mockito.atLeastOnce()).findRooms(captor.capture());
		return captor.getValue();
	}

	/**
	 * 조회 어댑터는 접속자 수 자리에 0을 채워 넘긴다(DB에 없는 값이다). 서비스가 그것을 실시간
	 * 값으로 덮지 않으면 목록·홈 카드의 시청자 수가 모든 방에서 0으로 고정된다.
	 */
	@Test
	void fillsViewerCountFromLiveSessionsInsteadOfThePersistencePlaceholder() {
		when(persistence.findRooms(any()))
			.thenReturn(new AuctionRoomListSlice(List.of(room("서연")), null, false));
		when(viewerCountQueryService.activeViewerCount(100L)).thenReturn(42L);

		AuctionRoomListResult result = service.getRooms(null, null, null, null, null);

		assertThat(result.items()).singleElement()
			.extracting(AuctionRoomSummaryItem::viewerCount)
			.isEqualTo(42);
	}

	/** 끝난 방은 세션 키가 이미 없어 0이다 — 페이지마다 헛되이 Redis를 왕복하지 않는다. */
	@Test
	void doesNotAskForViewerCountOfEndedRooms() {
		when(persistence.findRooms(any()))
			.thenReturn(new AuctionRoomListSlice(List.of(endedRoom()), null, false));

		AuctionRoomListResult result = service.getRooms(null, null, null, null, null);

		assertThat(result.items()).singleElement()
			.extracting(AuctionRoomSummaryItem::viewerCount)
			.isEqualTo(0);
		verify(viewerCountQueryService, never()).activeViewerCount(anyLong());
	}

	private static AuctionRoomRow endedRoom() {
		AuctionRoomRow live = room("서연");
		return new AuctionRoomRow(
			live.auctionId(),
			live.title(),
			live.thumbnailUrl(),
			live.categoryCode(),
			live.sellerNickname(),
			live.reservePrice(),
			live.bidCount(),
			live.bidParticipantCount(),
			live.predictionParticipantCount(),
			live.scheduledAt(),
			live.endAt(),
			live.soldPrice(),
			live.endedAt(),
			AuctionStatus.ENDED,
			live.viewerCount()
		);
	}

	private static AuctionRoomRow room(String sellerNickname) {
		return new AuctionRoomRow(
			100L,
			"키크론 Q1 Pro 경매",
			"https://cdn.example.com/products/501.png",
			"ELECTRONICS",
			sellerNickname,
			50_000L,
			7L,
			5L,
			15L,
			null,
			Instant.parse("2026-07-21T11:15:00Z"),
			null,
			null,
			AuctionStatus.LIVE,
			0
		);
	}

	private static AuctionRoomListErrorCode errorCodeOf(Throwable exception) {
		return ((AuctionRoomListException) exception).getErrorCode();
	}
}
