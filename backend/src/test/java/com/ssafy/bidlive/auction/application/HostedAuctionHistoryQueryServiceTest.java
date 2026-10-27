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

/** 개최 경매 공개 결과 변환, 닉네임 마스킹과 커서 오류 처리를 검증한다. */
class HostedAuctionHistoryQueryServiceTest {

	private HostedAuctionHistoryPersistence persistence;
	private HostedAuctionHistoryQueryService service;

	@BeforeEach
	void setUp() {
		persistence = mock(HostedAuctionHistoryPersistence.class);
		service = new HostedAuctionHistoryQueryService(persistence);
	}

	@Test
	void rejectsMalformedCursorBeforeQueryingDatabase() {
		assertThatThrownBy(() -> service.query(25L, "not*base64*", 8))
			.isInstanceOf(InvalidHostedAuctionCursorException.class);

		verify(persistence, never()).find(any());
	}

	@Test
	void mapsResultTypeAndMasksWinnerNickname() {
		when(persistence.find(any())).thenReturn(new HostedAuctionHistoryPage(
			List.of(
				row("SOLD", "TIME_OVER", "준호123"),
				row("CANCELLED", "HOST_LEFT", null),
				row("NO_SALE", "NO_BID", null)
			),
			"next",
			true
		));

		HostedAuctionHistorySlice result = service.query(25L, null, 8);

		assertThat(result.items())
			.extracting(HostedAuctionHistoryItem::resultType)
			.containsExactly("SOLD", "HOST_LEFT", "NO_BID");
		assertThat(result.items().get(0).winnerNickname()).isEqualTo("준호***");
		assertThat(result.items().get(1).winnerNickname()).isNull();
		assertThat(result.nextCursor()).isEqualTo("next");
		assertThat(result.hasNext()).isTrue();
	}

	private HostedAuctionHistoryRow row(
		String resultStatus,
		String terminationReason,
		String winnerNickname
	) {
		Instant now = Instant.parse("2026-07-21T11:00:00Z");
		return new HostedAuctionHistoryRow(
			100L,
			300L,
			"키크론 Q1 Pro",
			"https://cdn.example.com/products/abc.jpg",
			"ENDED",
			resultStatus,
			terminationReason,
			74_000L,
			winnerNickname,
			"WAITING_SHIPMENT",
			12L,
			9L,
			32L,
			now,
			now,
			now,
			now
		);
	}
}
