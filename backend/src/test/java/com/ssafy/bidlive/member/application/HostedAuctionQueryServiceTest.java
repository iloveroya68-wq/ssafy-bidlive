package com.ssafy.bidlive.member.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import com.ssafy.bidlive.auction.application.HostedAuctionHistoryItem;
import com.ssafy.bidlive.auction.application.HostedAuctionHistoryQuery;
import com.ssafy.bidlive.auction.application.HostedAuctionHistorySlice;
import com.ssafy.bidlive.auction.application.InvalidHostedAuctionCursorException;
import com.ssafy.bidlive.member.domain.HostedAuctionQueryErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 개최 경매 페이지 크기와 판매자 발송 액션 계산을 검증한다. */
class HostedAuctionQueryServiceTest {

	private HostedAuctionHistoryQuery historyQuery;
	private HostedAuctionQueryService service;

	@BeforeEach
	void setUp() {
		historyQuery = mock(HostedAuctionHistoryQuery.class);
		service = new HostedAuctionQueryService(historyQuery);
		when(historyQuery.query(anyLong(), isNull(), anyInt()))
			.thenReturn(new HostedAuctionHistorySlice(List.of(), null, false));
	}

	@Test
	void appliesDefaultAndAllowedSizeRange() {
		service.query(25L, null, null);
		service.query(25L, null, 100);
		service.query(25L, null, 0);

		verify(historyQuery).query(25L, null, 8);
		verify(historyQuery).query(25L, null, 20);
		verify(historyQuery).query(25L, null, 1);
	}

	@Test
	void exposesShipmentConfirmationOnlyWhenSellerMustAct() {
		when(historyQuery.query(25L, null, 8)).thenReturn(
			new HostedAuctionHistorySlice(
				List.of(
					item("WAITING_SHIPMENT"),
					item("SHIPPED")
				),
				null,
				false
			)
		);

		HostedAuctionQueryResult result = service.query(25L, null, null);

		assertThat(result.items().get(0))
			.satisfies(item -> {
				assertThat(item.actionRequired()).isTrue();
				assertThat(item.actionCode()).isEqualTo("CONFIRM_SHIPMENT");
				assertThat(item.actionLabel()).isEqualTo("발송 확인");
			});
		assertThat(result.items().get(1))
			.satisfies(item -> {
				assertThat(item.actionRequired()).isFalse();
				assertThat(item.actionCode()).isNull();
				assertThat(item.actionLabel()).isNull();
			});
	}

	@Test
	void translatesAuctionCursorFailureToMemberApiError() {
		when(historyQuery.query(25L, "bad", 8))
			.thenThrow(new InvalidHostedAuctionCursorException(
				new IllegalArgumentException("cursor")
			));

		assertThatThrownBy(() -> service.query(25L, "bad", null))
			.isInstanceOf(HostedAuctionQueryException.class)
			.satisfies(exception -> assertThat(
				((HostedAuctionQueryException) exception).getErrorCode()
			).isEqualTo(HostedAuctionQueryErrorCode.INVALID_CURSOR));
	}

	@Test
	void rejectsInvalidAuthenticatedMemberId() {
		assertThatThrownBy(() -> service.query(null, null, null))
			.isInstanceOf(HostedAuctionQueryException.class)
			.satisfies(exception -> assertThat(
				((HostedAuctionQueryException) exception).getErrorCode()
			).isEqualTo(HostedAuctionQueryErrorCode.UNAUTHORIZED));
	}

	private HostedAuctionHistoryItem item(String dealStatus) {
		Instant now = Instant.parse("2026-07-21T11:00:00Z");
		return new HostedAuctionHistoryItem(
			100L,
			300L,
			"키크론 Q1 Pro",
			"https://cdn.example.com/products/abc.jpg",
			"ENDED",
			"SOLD",
			74_000L,
			"준호***",
			dealStatus,
			12L,
			9L,
			32L,
			now,
			now,
			now
		);
	}
}
