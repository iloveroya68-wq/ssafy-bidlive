package com.ssafy.bidlive.member.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import com.ssafy.bidlive.auction.application.InvalidParticipatedAuctionCursorException;
import com.ssafy.bidlive.auction.application.ParticipatedAuctionHistoryQuery;
import com.ssafy.bidlive.auction.application.ParticipatedAuctionHistorySlice;
import com.ssafy.bidlive.member.domain.ParticipatedAuctionQueryErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 참여 경매 페이지 크기 제한과 조회 오류의 API 오류 변환을 검증한다. */
class ParticipatedAuctionQueryServiceTest {

	private ParticipatedAuctionHistoryQuery historyQuery;
	private ParticipatedAuctionQueryService service;

	@BeforeEach
	void setUp() {
		historyQuery = mock(ParticipatedAuctionHistoryQuery.class);
		service = new ParticipatedAuctionQueryService(historyQuery);
		when(historyQuery.query(anyLong(), isNull(), anyInt()))
			.thenReturn(
				new ParticipatedAuctionHistorySlice(
					List.of(),
					null,
					false
				)
			);
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
	void translatesAuctionCursorFailureToMemberApiError() {
		when(historyQuery.query(25L, "bad", 8))
			.thenThrow(new InvalidParticipatedAuctionCursorException(
				new IllegalArgumentException("cursor")
			));

		assertThatThrownBy(() -> service.query(25L, "bad", null))
			.isInstanceOf(ParticipatedAuctionQueryException.class)
			.satisfies(exception -> assertThat(
				((ParticipatedAuctionQueryException) exception)
					.getErrorCode()
			).isEqualTo(ParticipatedAuctionQueryErrorCode.INVALID_CURSOR));
	}

	@Test
	void rejectsInvalidAuthenticatedMemberId() {
		assertThatThrownBy(() -> service.query(null, null, null))
			.isInstanceOf(ParticipatedAuctionQueryException.class)
			.satisfies(exception -> assertThat(
				((ParticipatedAuctionQueryException) exception)
					.getErrorCode()
			).isEqualTo(ParticipatedAuctionQueryErrorCode.UNAUTHORIZED));
	}
}
