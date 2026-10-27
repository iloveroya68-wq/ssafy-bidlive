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
import com.ssafy.bidlive.auction.application.AuctionRoomJoinException;
import com.ssafy.bidlive.auction.application.AuctionRoomJoinResult;
import com.ssafy.bidlive.auction.application.AuctionRoomJoinService;
import com.ssafy.bidlive.auction.domain.AuctionRoomJoinErrorCode;
import com.ssafy.bidlive.auction.domain.AuctionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * 대기방·진행 중 두 응답 모양과 오류 상태를 검증한다. 명세가 두 경우의 **필드 집합을 다르게**
 * 정했으므로, 해당 없는 필드가 `null`로라도 나가면 안 된다.
 */
class AuctionRoomJoinControllerTest {

	private static final String PATH = "/api/v1/auction-rooms/100/join";

	private AuctionRoomJoinService joinService;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		joinService = mock(AuctionRoomJoinService.class);
		mockMvc = MockMvcBuilders.standaloneSetup(new AuctionRoomJoinController(joinService))
			.setControllerAdvice(new AuctionRoomJoinExceptionHandler())
			.setMessageConverters(new MappingJackson2HttpMessageConverter(
				Jackson2ObjectMapperBuilder.json()
					.featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
					.build()))
			.build();
	}

	@Test
	void returnsWaitingRoomShapeWithDestinations() throws Exception {
		when(joinService.joinRoom(anyLong(), any())).thenReturn(new AuctionRoomJoinResult(
			Instant.parse("2026-07-20T11:59:30Z"),
			100L,
			AuctionStatus.WAITING,
			Instant.parse("2026-07-20T12:00:00Z"),
			true,
			false,
			false,
			"/topic/auctions/100",
			"/topic/auctions/100/chat",
			"/topic/auctions/100/viewers",
			null
		));

		mockMvc.perform(post(PATH).principal(authenticated("77")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.serverTime").value("2026-07-20T20:59:30+09:00"))
			.andExpect(jsonPath("$.auctionId").value(100))
			.andExpect(jsonPath("$.roomStatus").value("WAITING"))
			.andExpect(jsonPath("$.scheduledAt").value("2026-07-20T21:00:00+09:00"))
			.andExpect(jsonPath("$.permissions.chat").value(true))
			.andExpect(jsonPath("$.permissions.bid").value(false))
			.andExpect(jsonPath("$.permissions.prediction").value(false))
			.andExpect(jsonPath("$.destinations.room").value("/topic/auctions/100"))
			.andExpect(jsonPath("$.destinations.chat").value("/topic/auctions/100/chat"))
			.andExpect(jsonPath("$.destinations.viewers").value("/topic/auctions/100/viewers"))
			// 대기방 응답에는 요약 가능 여부가 없다
			.andExpect(jsonPath("$.catchUpSummaryAvailable").doesNotExist());

		verify(joinService).joinRoom(100L, 77L);
	}

	@Test
	void returnsLiveRoomShapeWithoutDestinations() throws Exception {
		when(joinService.joinRoom(anyLong(), any())).thenReturn(new AuctionRoomJoinResult(
			Instant.parse("2026-07-20T12:05:00Z"),
			100L,
			AuctionStatus.LIVE,
			null,
			true,
			true,
			true,
			null,
			null,
			null,
			false
		));

		mockMvc.perform(post(PATH).principal(authenticated("77")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.roomStatus").value("LIVE"))
			.andExpect(jsonPath("$.permissions.bid").value(true))
			.andExpect(jsonPath("$.permissions.prediction").value(true))
			.andExpect(jsonPath("$.catchUpSummaryAvailable").value(false))
			// 진행 중 응답에는 예정 시각·구독 목적지가 없다
			.andExpect(jsonPath("$.scheduledAt").doesNotExist())
			.andExpect(jsonPath("$.destinations").doesNotExist());
	}

	@Test
	void rejectsNonNumericPrincipalAsUnauthorized() throws Exception {
		mockMvc.perform(post(PATH).principal(authenticated("anonymousUser")))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	@Test
	void mapsErrorCodesToStatuses() throws Exception {
		assertMaps(AuctionRoomJoinErrorCode.ROOM_ACCESS_DENIED, 403);
		assertMaps(AuctionRoomJoinErrorCode.AUCTION_ROOM_NOT_FOUND, 404);
		assertMaps(AuctionRoomJoinErrorCode.AUCTION_ALREADY_ENDED, 409);
	}

	private void assertMaps(AuctionRoomJoinErrorCode errorCode, int expectedStatus)
		throws Exception {
		org.mockito.Mockito.doThrow(new AuctionRoomJoinException(errorCode))
			.when(joinService).joinRoom(anyLong(), any());

		mockMvc.perform(post(PATH).principal(authenticated("77")))
			.andExpect(status().is(expectedStatus))
			.andExpect(jsonPath("$.code").value(errorCode.name()));
	}

	private static Principal authenticated(String name) {
		return new UsernamePasswordAuthenticationToken(name, "n/a", java.util.List.of());
	}
}
