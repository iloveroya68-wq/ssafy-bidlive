package com.ssafy.bidlive.auction.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.ssafy.bidlive.auction.application.CatchUpSummaryException;
import com.ssafy.bidlive.auction.application.CatchUpSummaryResult;
import com.ssafy.bidlive.auction.application.CatchUpSummaryService;
import com.ssafy.bidlive.auction.domain.CatchUpSummaryErrorCode;
import com.ssafy.bidlive.auction.domain.CatchUpSummarySourceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CatchUpSummaryControllerTest {

	private static final String PATH = "/api/v1/auction-rooms/100/catch-up-summary";

	private CatchUpSummaryService service;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		service = mock(CatchUpSummaryService.class);
		mockMvc = MockMvcBuilders.standaloneSetup(new CatchUpSummaryController(service))
			.setControllerAdvice(new CatchUpSummaryExceptionHandler())
			.setMessageConverters(new MappingJackson2HttpMessageConverter(
				Jackson2ObjectMapperBuilder.json()
					.featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
					.build()))
			.build();
	}

	@Test
	void returnsCachedSummaryShape() throws Exception {
		when(service.getSummary(100L, 77L)).thenReturn(Optional.of(summary()));

		mockMvc.perform(get(PATH).principal(authenticated("77")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.auctionId").value(100))
			.andExpect(jsonPath("$.generatedAt").value("2026-07-20T21:08:00+09:00"))
			.andExpect(jsonPath("$.bidSummary.bidCount").value(7))
			.andExpect(jsonPath("$.extensionSummary.extendedMinutes").value(5))
			.andExpect(jsonPath("$.sellerMentions[0]").value("키캡 하나에 하자가 있습니다"))
			.andExpect(jsonPath("$.sourceType").value("STT_AND_ROOM_EVENT"));

		verify(service).getSummary(100L, 77L);
	}

	@Test
	void returnsNoContentWhenThereIsNoSource() throws Exception {
		when(service.getSummary(100L, 77L)).thenReturn(Optional.empty());

		mockMvc.perform(get(PATH).principal(authenticated("77")))
			.andExpect(status().isNoContent())
			.andExpect(content().string(""));
	}

	@Test
	void mapsAccessAndStateErrors() throws Exception {
		assertMaps(CatchUpSummaryErrorCode.ROOM_ACCESS_DENIED, 403);
		assertMaps(CatchUpSummaryErrorCode.AUCTION_ROOM_NOT_FOUND, 404);
		assertMaps(CatchUpSummaryErrorCode.AUCTION_NOT_STARTED, 409);
	}

	private void assertMaps(CatchUpSummaryErrorCode code, int expectedStatus) throws Exception {
		reset(service);
		org.mockito.Mockito.doThrow(new CatchUpSummaryException(code))
			.when(service).getSummary(anyLong(), any());

		mockMvc.perform(get(PATH).principal(authenticated("77")))
			.andExpect(status().is(expectedStatus))
			.andExpect(jsonPath("$.code").value(code.name()));
	}

	private static CatchUpSummaryResult summary() {
		return new CatchUpSummaryResult(
			100,
			Instant.parse("2026-07-20T12:08:00Z"),
			Instant.parse("2026-07-20T12:10:00Z"),
			7,
			5,
			1,
			5,
			List.of("키캡 하나에 하자가 있습니다"),
			Instant.parse("2026-07-20T12:08:12Z"),
			Instant.parse("2026-07-20T12:15:00Z"),
			CatchUpSummarySourceType.STT_AND_ROOM_EVENT
		);
	}

	private static UsernamePasswordAuthenticationToken authenticated(String name) {
		return new UsernamePasswordAuthenticationToken(name, "n/a", List.of());
	}
}
