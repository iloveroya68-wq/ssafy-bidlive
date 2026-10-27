package com.ssafy.bidlive.auction.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 낙찰 경매 커서 검증과 판매자 닉네임 마스킹을 검증한다. */
class WonAuctionHistoryQueryServiceTest {

	private WonAuctionHistoryPersistence persistence;
	private WonAuctionHistoryQueryService service;

	@BeforeEach
	void setUp() {
		persistence = mock(WonAuctionHistoryPersistence.class);
		service = new WonAuctionHistoryQueryService(persistence);
	}

	@Test
	void rejectsMalformedCursorBeforeQueryingDatabase() {
		assertThatThrownBy(() -> service.query(25L, "not*base64*", 8))
			.isInstanceOf(InvalidWonAuctionCursorException.class);

		verify(persistence, never()).find(any());
	}

	@Test
	void masksSellerNicknameAndPreservesPageInformation() {
		Instant endedAt = Instant.parse("2026-07-21T11:17:13Z");
		when(persistence.find(any())).thenReturn(
			new WonAuctionHistoryPage(
				List.of(new WonAuctionHistoryRow(
					100L,
					300L,
					"키크론 Q1 Pro",
					"https://cdn.example.com/products/abc.jpg",
					"서연123",
					74_000L,
					82_000L,
					"WAITING_ADDRESS",
					"https://cdn.example.com/snapshots/100.jpg",
					endedAt
				)),
				"next",
				true
			)
		);

		WonAuctionHistorySlice result = service.query(25L, null, 8);

		assertThat(result.items()).singleElement()
			.satisfies(item -> {
				assertThat(item.auctionId()).isEqualTo(100L);
				assertThat(item.dealId()).isEqualTo(300L);
				assertThat(item.sellerNickname()).isEqualTo("서연***");
				assertThat(item.finalPrice()).isEqualTo(74_000L);
				assertThat(item.myFinalBid()).isEqualTo(82_000L);
				assertThat(item.dealStatus())
					.isEqualTo("WAITING_ADDRESS");
				assertThat(item.endedAt()).isEqualTo(endedAt);
			});
		assertThat(result.nextCursor()).isEqualTo("next");
		assertThat(result.hasNext()).isTrue();
	}
}
