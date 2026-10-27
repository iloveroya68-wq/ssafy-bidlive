package com.ssafy.bidlive.auction.api;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.ssafy.bidlive.auction.application.UpcomingAuctionItem;
import com.ssafy.bidlive.auction.application.UpcomingAuctionQueryService;
import com.ssafy.bidlive.auction.application.UpcomingAuctionResult;
import com.ssafy.bidlive.auction.domain.AuctionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** 공개 곧 시작 경매 조회의 쿼리 전달과 JSON 응답 계약을 검증합니다. */
class UpcomingAuctionControllerTest {

	private UpcomingAuctionQueryService queryService;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		queryService = mock(UpcomingAuctionQueryService.class);
		mockMvc = MockMvcBuilders.standaloneSetup(
			new UpcomingAuctionController(queryService)
		)
			.setMessageConverters(new MappingJackson2HttpMessageConverter(
				Jackson2ObjectMapperBuilder.json()
					.featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
					.build()))
			.build();
	}

	@Test
	void returnsUpcomingAuctionsAndCountMetadata() throws Exception {
		when(queryService.getUpcoming(5)).thenReturn(new UpcomingAuctionResult(
			List.of(new UpcomingAuctionItem(
				100L,
				"키크론 Q1 Pro",
				"https://cdn.example.com/products/abc.jpg",
				Instant.parse("2026-07-21T11:00:00Z"),
				18L,
				AuctionStatus.WAITING
			)),
			8L,
			7L
		));

		mockMvc.perform(get("/api/v1/auction-rooms/upcoming").param("limit", "5"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(1))
			.andExpect(jsonPath("$.items[0].auctionId").value(100))
			.andExpect(jsonPath("$.items[0].productTitle").value("키크론 Q1 Pro"))
			.andExpect(jsonPath("$.items[0].imageUrl")
				.value("https://cdn.example.com/products/abc.jpg"))
			.andExpect(jsonPath("$.items[0].scheduledAt")
				.value("2026-07-21T20:00:00+09:00"))
			.andExpect(jsonPath("$.items[0].waitingCount").value(18))
			.andExpect(jsonPath("$.items[0].status").value("WAITING"))
			.andExpect(jsonPath("$.totalCount").value(8))
			.andExpect(jsonPath("$.hiddenCount").value(7));

		verify(queryService).getUpcoming(5);
	}

	@Test
	void passesNullWhenLimitIsOmitted() throws Exception {
		when(queryService.getUpcoming(null))
			.thenReturn(new UpcomingAuctionResult(List.of(), 0L, 0L));

		mockMvc.perform(get("/api/v1/auction-rooms/upcoming"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items").isEmpty())
			.andExpect(jsonPath("$.totalCount").value(0))
			.andExpect(jsonPath("$.hiddenCount").value(0));

		verify(queryService).getUpcoming(null);
	}
}
