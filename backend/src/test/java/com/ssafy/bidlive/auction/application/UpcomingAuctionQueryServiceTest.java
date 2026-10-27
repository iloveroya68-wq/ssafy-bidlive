package com.ssafy.bidlive.auction.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import com.ssafy.bidlive.auction.domain.AuctionStatus;
import com.ssafy.bidlive.media.application.ViewerCountQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** 한 시간 조회 범위, 최대 반환 수와 숨김 개수 계산을 검증합니다. */
class UpcomingAuctionQueryServiceTest {

	private static final Instant NOW = Instant.parse("2026-07-21T10:00:00Z");

	private UpcomingAuctionPersistence persistence;
	private ViewerCountQueryService viewerCountQueryService;
	private UpcomingAuctionQueryService service;

	@BeforeEach
	void setUp() {
		persistence = mock(UpcomingAuctionPersistence.class);
		viewerCountQueryService = mock(ViewerCountQueryService.class);
		service = new UpcomingAuctionQueryService(
			persistence,
			viewerCountQueryService,
			Clock.fixed(NOW, ZoneOffset.UTC)
		);
		when(persistence.findUpcoming(any(), any(), anyInt()))
			.thenReturn(new UpcomingAuctionSnapshot(List.of(), 0L));
	}

	@Test
	void queriesFromNowThroughOneHourAndUsesDefaultLimit() {
		service.getUpcoming(null);

		verify(persistence).findUpcoming(
			NOW,
			Instant.parse("2026-07-21T11:00:00Z"),
			5
		);
	}

	@Test
	void clampsRequestedLimitToOneThroughFive() {
		service.getUpcoming(0);
		service.getUpcoming(100);

		ArgumentCaptor<Integer> limitCaptor = ArgumentCaptor.forClass(Integer.class);
		verify(persistence, org.mockito.Mockito.times(2))
			.findUpcoming(any(), any(), limitCaptor.capture());
		assertThat(limitCaptor.getAllValues()).containsExactly(1, 5);
	}

	@Test
	void calculatesHiddenCountFromTotalAndReturnedItems() {
		UpcomingAuctionRow row = new UpcomingAuctionRow(
			100L,
			"키크론 Q1 Pro",
			"https://cdn.example.com/products/abc.jpg",
			Instant.parse("2026-07-21T10:30:00Z"),
			AuctionStatus.WAITING
		);
		when(persistence.findUpcoming(any(), any(), anyInt()))
			.thenReturn(new UpcomingAuctionSnapshot(List.of(row), 8L));
		when(viewerCountQueryService.activeViewerCount(100L)).thenReturn(18L);

		UpcomingAuctionResult result = service.getUpcoming(5);

		assertThat(result.items()).singleElement()
			.satisfies(item -> {
				assertThat(item.auctionId()).isEqualTo(100L);
				assertThat(item.waitingCount()).isEqualTo(18L);
			});
		assertThat(result.totalCount()).isEqualTo(8L);
		assertThat(result.hiddenCount()).isEqualTo(7L);
		verify(viewerCountQueryService).activeViewerCount(100L);
	}

	@Test
	void returnsZeroHiddenCountWhenNoAuctionExists() {
		UpcomingAuctionResult result = service.getUpcoming(5);

		assertThat(result.items()).isEmpty();
		assertThat(result.totalCount()).isZero();
		assertThat(result.hiddenCount()).isZero();
	}
}
