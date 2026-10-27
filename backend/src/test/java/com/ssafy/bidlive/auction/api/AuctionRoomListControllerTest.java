package com.ssafy.bidlive.auction.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.ssafy.bidlive.auction.application.AuctionRoomListException;
import com.ssafy.bidlive.auction.application.AuctionRoomListQueryService;
import com.ssafy.bidlive.auction.application.AuctionRoomListResult;
import com.ssafy.bidlive.auction.application.AuctionRoomStatusCounts;
import com.ssafy.bidlive.auction.application.AuctionRoomSummaryItem;
import com.ssafy.bidlive.auction.domain.AuctionRoomListErrorCode;
import com.ssafy.bidlive.auction.domain.AuctionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** 목록 응답의 필드 계약(serverTime·counts 키)과 오류 코드별 HTTP 상태를 검증한다. */
class AuctionRoomListControllerTest {

	private AuctionRoomListQueryService queryService;
	private MockMvc mockMvc;

	/**
	 * standaloneSetup은 Spring Boot의 Jackson 자동 설정을 쓰지 않아 시각이 epoch 숫자로
	 * 직렬화된다. WRITE_DATES_AS_TIMESTAMPS 비활성은 Boot 기본값이므로 여기서 같은 값을 명시해
	 * 실제 응답과 같은 ISO-8601 형식을 검증한다.
	 */
	@BeforeEach
	void setUp() {
		queryService = mock(AuctionRoomListQueryService.class);
		mockMvc = MockMvcBuilders.standaloneSetup(
			new AuctionRoomListController(queryService)
		)
			.setControllerAdvice(new AuctionRoomListExceptionHandler())
			.setMessageConverters(new MappingJackson2HttpMessageConverter(
				Jackson2ObjectMapperBuilder.json()
					.featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
					.build()))
			.build();
	}

	@Test
	void returnsServerTimeAndStatusCountsWithoutAuthentication() throws Exception {
		when(queryService.getRooms(any(), any(), any(), any(), any())).thenReturn(
			new AuctionRoomListResult(
				Instant.parse("2026-07-21T10:59:30Z"),
				List.of(new AuctionRoomSummaryItem(
					100L,
					"키크론 Q1 Pro 경매",
					"https://cdn.example.com/products/501.png",
					"ELECTRONICS",
					"서연***",
					50_000L,
					7L,
					5L,
					15L,
					null,
					Instant.parse("2026-07-21T11:15:00Z"),
					null,
					null,
					AuctionStatus.LIVE,
					18
				)),
				"opaque-cursor",
				true,
				new AuctionRoomStatusCounts(3L, 2L)
			)
		);

		mockMvc.perform(get("/api/v1/auction-rooms"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.serverTime").value("2026-07-21T19:59:30+09:00"))
			.andExpect(jsonPath("$.items.length()").value(1))
			.andExpect(jsonPath("$.items[0].auctionId").value(100))
			.andExpect(jsonPath("$.items[0].categoryCode").value("ELECTRONICS"))
			.andExpect(jsonPath("$.items[0].bidCount").value(7))
			.andExpect(jsonPath("$.items[0].bidParticipantCount").value(5))
			.andExpect(jsonPath("$.items[0].predictionParticipantCount").value(15))
			.andExpect(jsonPath("$.items[0].sellerNickname").value("서연***"))
			.andExpect(jsonPath("$.items[0].endAt").value("2026-07-21T20:15:00+09:00"))
			.andExpect(jsonPath("$.items[0].soldPrice").value(org.hamcrest.Matchers.nullValue()))
			.andExpect(jsonPath("$.items[0].scheduledAt").value(org.hamcrest.Matchers.nullValue()))
			.andExpect(jsonPath("$.items[0].status").value("LIVE"))
			.andExpect(jsonPath("$.nextCursor").value("opaque-cursor"))
			.andExpect(jsonPath("$.hasNext").value(true))
			.andExpect(jsonPath("$.counts.LIVE").value(3))
			.andExpect(jsonPath("$.counts.WAITING").value(2));
	}

	@Test
	void passesQueryParametersThrough() throws Exception {
		when(queryService.getRooms(any(), any(), any(), any(), any())).thenReturn(
			new AuctionRoomListResult(
				Instant.parse("2026-07-21T10:59:30Z"),
				List.of(),
				null,
				false,
				new AuctionRoomStatusCounts(0L, 0L)
			)
		);

		mockMvc.perform(get("/api/v1/auction-rooms")
				.param("status", "LIVE")
				.param("categoryId", "12")
				.param("keyword", "키보드")
				.param("cursor", "abc")
				.param("size", "20"))
			.andExpect(status().isOk());

		verify(queryService).getRooms("LIVE", 12L, "키보드", "abc", 20);
	}

	@Test
	void mapsInvalidStatusToBadRequest() throws Exception {
		when(queryService.getRooms(any(), any(), any(), any(), any()))
			.thenThrow(new AuctionRoomListException(
				AuctionRoomListErrorCode.INVALID_AUCTION_STATUS
			));

		mockMvc.perform(get("/api/v1/auction-rooms").param("status", "PAUSED"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_AUCTION_STATUS"));
	}

	@Test
	void mapsInvalidCursorToBadRequest() throws Exception {
		when(queryService.getRooms(any(), any(), any(), any(), any()))
			.thenThrow(new AuctionRoomListException(AuctionRoomListErrorCode.INVALID_CURSOR));

		mockMvc.perform(get("/api/v1/auction-rooms").param("cursor", "broken"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_CURSOR"));
	}

	@Test
	void mapsUnknownCategoryToNotFound() throws Exception {
		when(queryService.getRooms(any(), eq(999L), any(), any(), any()))
			.thenThrow(new AuctionRoomListException(AuctionRoomListErrorCode.CATEGORY_NOT_FOUND));

		mockMvc.perform(get("/api/v1/auction-rooms").param("categoryId", "999"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"));
	}
}
