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

import com.ssafy.bidlive.auction.application.InvalidWonAuctionCursorException;
import com.ssafy.bidlive.auction.application.WonAuctionHistoryItem;
import com.ssafy.bidlive.auction.application.WonAuctionHistoryQuery;
import com.ssafy.bidlive.auction.application.WonAuctionHistorySlice;
import com.ssafy.bidlive.member.domain.WonAuctionQueryErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 낙찰 경매 페이지 크기와 구매자 액션 및 오류 변환을 검증한다. */
class WonAuctionQueryServiceTest {

	private WonAuctionHistoryQuery historyQuery;
	private WonAuctionQueryService service;

	@BeforeEach
	void setUp() {
		historyQuery = mock(WonAuctionHistoryQuery.class);
		service = new WonAuctionQueryService(historyQuery);
		when(historyQuery.query(anyLong(), isNull(), anyInt()))
			.thenReturn(new WonAuctionHistorySlice(List.of(), null, false));
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
	void mapsBuyerActionsFromDealStatus() {
		when(historyQuery.query(25L, null, 8)).thenReturn(
			new WonAuctionHistorySlice(
				List.of(
					item(100L, "WAITING_ADDRESS"),
					item(101L, "WAITING_RECEIPT"),
					item(102L, "COMPLETED")
				),
				null,
				false
			)
		);

		WonAuctionQueryResult result = service.query(25L, null, null);

		assertThat(result.items().get(0))
			.satisfies(item -> {
				assertThat(item.actionRequired()).isTrue();
				assertThat(item.actionCode()).isEqualTo("SUBMIT_ADDRESS");
				assertThat(item.actionLabel()).isEqualTo("배송지 입력");
			});
		assertThat(result.items().get(1))
			.satisfies(item -> {
				assertThat(item.actionRequired()).isTrue();
				assertThat(item.actionCode()).isEqualTo("CONFIRM_RECEIPT");
				assertThat(item.actionLabel()).isEqualTo("수령 확인");
			});
		assertThat(result.items().get(2))
			.satisfies(item -> {
				assertThat(item.actionRequired()).isFalse();
				assertThat(item.actionCode()).isNull();
				assertThat(item.actionLabel()).isNull();
			});
	}

	@Test
	void translatesAuctionCursorFailureToMemberApiError() {
		when(historyQuery.query(25L, "bad", 8))
			.thenThrow(new InvalidWonAuctionCursorException(
				new IllegalArgumentException("cursor")
			));

		assertThatThrownBy(() -> service.query(25L, "bad", null))
			.isInstanceOf(WonAuctionQueryException.class)
			.satisfies(exception -> assertThat(
				((WonAuctionQueryException) exception).getErrorCode()
			).isEqualTo(WonAuctionQueryErrorCode.INVALID_CURSOR));
	}

	@Test
	void rejectsInvalidAuthenticatedMemberId() {
		assertThatThrownBy(() -> service.query(null, null, null))
			.isInstanceOf(WonAuctionQueryException.class)
			.satisfies(exception -> assertThat(
				((WonAuctionQueryException) exception).getErrorCode()
			).isEqualTo(WonAuctionQueryErrorCode.UNAUTHORIZED));
	}

	private WonAuctionHistoryItem item(long auctionId, String dealStatus) {
		return new WonAuctionHistoryItem(
			auctionId,
			auctionId + 200,
			"상품",
			"https://cdn.example.com/products/image.jpg",
			"판매***",
			74_000L,
			82_000L,
			dealStatus,
			"https://cdn.example.com/snapshots/image.jpg",
			Instant.parse("2026-07-21T11:17:13Z")
		);
	}
}
