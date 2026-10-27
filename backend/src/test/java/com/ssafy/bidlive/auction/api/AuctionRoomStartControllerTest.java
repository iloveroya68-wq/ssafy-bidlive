package com.ssafy.bidlive.auction.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.Principal;
import java.time.Instant;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.ssafy.bidlive.auction.application.AuctionRoomStartException;
import com.ssafy.bidlive.auction.application.AuctionRoomStartResult;
import com.ssafy.bidlive.auction.application.AuctionRoomStartService;
import com.ssafy.bidlive.auction.domain.AuctionRoomStartErrorCode;
import com.ssafy.bidlive.auction.domain.AuctionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** 시작 응답 필드와 오류 코드별 HTTP 상태(422 포함)를 검증한다. */
class AuctionRoomStartControllerTest {

	private static final String PATH = "/api/v1/auction-rooms/100/start";

	private AuctionRoomStartService startService;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		startService = mock(AuctionRoomStartService.class);
		mockMvc = MockMvcBuilders.standaloneSetup(new AuctionRoomStartController(startService))
			.setControllerAdvice(new AuctionRoomStartExceptionHandler())
			.setMessageConverters(new MappingJackson2HttpMessageConverter(
				Jackson2ObjectMapperBuilder.json()
					.featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
					.build()))
			.build();
		when(startService.startRoom(anyLong(), any())).thenReturn(new AuctionRoomStartResult(
			100L,
			AuctionStatus.LIVE,
			Instant.parse("2026-07-21T11:02:13Z"),
			Instant.parse("2026-07-21T11:17:13Z"),
			15,
			Instant.parse("2026-07-21T11:02:13Z")
		));
	}

	@Test
	void returnsStartInfoWithServerTime() throws Exception {
		mockMvc.perform(post(PATH).principal(authenticated("25")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.auctionId").value(100))
			.andExpect(jsonPath("$.status").value("LIVE"))
			.andExpect(jsonPath("$.startedAt").value("2026-07-21T20:02:13+09:00"))
			.andExpect(jsonPath("$.endAt").value("2026-07-21T20:17:13+09:00"))
			.andExpect(jsonPath("$.durationMinutes").value(15))
			.andExpect(jsonPath("$.serverTime").value("2026-07-21T20:02:13+09:00"));

		verify(startService).startRoom(100L, 25L);
	}

	@Test
	void rejectsNonNumericPrincipalAsUnauthorized() throws Exception {
		mockMvc.perform(post(PATH).principal(authenticated("anonymousUser")))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	@Test
	void mapsErrorCodesToStatuses() throws Exception {
		assertMaps(AuctionRoomStartErrorCode.NOT_AUCTION_SELLER, 403);
		assertMaps(AuctionRoomStartErrorCode.AUCTION_NOT_FOUND, 404);
		assertMaps(AuctionRoomStartErrorCode.AUCTION_ALREADY_STARTED, 409);
		assertMaps(AuctionRoomStartErrorCode.AUCTION_START_NOT_ALLOWED, 409);
		assertMaps(AuctionRoomStartErrorCode.AUCTION_MODERATION_BLOCKED, 422);
	}

	private void assertMaps(AuctionRoomStartErrorCode errorCode, int expectedStatus)
		throws Exception {
		org.mockito.Mockito.doThrow(new AuctionRoomStartException(errorCode))
			.when(startService).startRoom(anyLong(), any());

		mockMvc.perform(post(PATH).principal(authenticated("25")))
			.andExpect(status().is(expectedStatus))
			.andExpect(jsonPath("$.code").value(errorCode.name()));
	}

	private static Principal authenticated(String name) {
		return new UsernamePasswordAuthenticationToken(name, "n/a", java.util.List.of());
	}
}
