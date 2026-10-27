package com.ssafy.bidlive.auction.api;

import static org.hamcrest.Matchers.nullValue;
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
import com.ssafy.bidlive.auction.application.AuctionRoomDetailException;
import com.ssafy.bidlive.auction.application.AuctionRoomDetailQueryService;
import com.ssafy.bidlive.auction.application.AuctionRoomDetailResult;
import com.ssafy.bidlive.auction.application.AuctionRoomViewerContext;
import com.ssafy.bidlive.auction.domain.AuctionRoomDetailErrorCode;
import com.ssafy.bidlive.auction.domain.AuctionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** 상세 응답 필드와 선택 인증 사용자별 조회 컨텍스트 및 오류 상태를 검증합니다. */
class AuctionRoomDetailControllerTest {

	private AuctionRoomDetailQueryService queryService;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		queryService = mock(AuctionRoomDetailQueryService.class);
		mockMvc = MockMvcBuilders.standaloneSetup(
			new AuctionRoomDetailController(queryService)
		)
			.setControllerAdvice(new AuctionRoomDetailExceptionHandler())
			.setMessageConverters(new MappingJackson2HttpMessageConverter(
				Jackson2ObjectMapperBuilder.json()
					.featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
					.build()))
			.build();
		when(queryService.getRoom(eq(100L), any())).thenReturn(result());
	}

	@Test
	void returnsPublicDetailWithServerTimeForAnonymousViewer() throws Exception {
		mockMvc.perform(get("/api/v1/auction-rooms/100"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.serverTime").value("2026-07-21T19:59:30+09:00"))
			.andExpect(jsonPath("$.auctionId").value(100))
			.andExpect(jsonPath("$.status").value("WAITING"))
			.andExpect(jsonPath("$.scheduledAt").value("2026-07-21T20:00:00+09:00"))
			.andExpect(jsonPath("$.viewerCount").value(18))
			.andExpect(jsonPath("$.isSeller").value(true))
			.andExpect(jsonPath("$.notificationCount").value(12))
			.andExpect(jsonPath("$.product.productId").value(501))
			.andExpect(jsonPath("$.product.categoryCode").value("ELECTRONICS"))
			.andExpect(jsonPath("$.seller.nickname").value("판**"))
			.andExpect(jsonPath("$.seller.rating").value(nullValue()))
			.andExpect(jsonPath("$.sellerInfo").doesNotExist())
			.andExpect(jsonPath("$.canEnter").value(true));

		ArgumentCaptor<AuctionRoomViewerContext> captor =
			ArgumentCaptor.forClass(AuctionRoomViewerContext.class);
		verify(queryService).getRoom(eq(100L), captor.capture());
		org.assertj.core.api.Assertions.assertThat(captor.getValue().memberId()).isNull();
		org.assertj.core.api.Assertions.assertThat(captor.getValue().admin()).isFalse();
	}

	@Test
	void passesAuthenticatedAdminContext() throws Exception {
		UsernamePasswordAuthenticationToken authentication =
			UsernamePasswordAuthenticationToken.authenticated(
				"25",
				"token",
				List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
			);

		mockMvc.perform(get("/api/v1/auction-rooms/100").principal(authentication))
			.andExpect(status().isOk());

		verify(queryService).getRoom(
			100L,
			AuctionRoomViewerContext.authenticated(25L, true)
		);
	}

	@Test
	void mapsMalformedAndNonPositiveAuctionIdsToBadRequest() throws Exception {
		mockMvc.perform(get("/api/v1/auction-rooms/not-a-number"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_AUCTION_ID"));
		mockMvc.perform(get("/api/v1/auction-rooms/0"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_AUCTION_ID"));
	}

	@Test
	void mapsMissingAndDeletedRoomsToContractStatus() throws Exception {
		when(queryService.getRoom(eq(404L), any())).thenThrow(
			new AuctionRoomDetailException(AuctionRoomDetailErrorCode.AUCTION_NOT_FOUND)
		);
		when(queryService.getRoom(eq(410L), any())).thenThrow(
			new AuctionRoomDetailException(AuctionRoomDetailErrorCode.AUCTION_DELETED)
		);

		mockMvc.perform(get("/api/v1/auction-rooms/404"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("AUCTION_NOT_FOUND"));
		mockMvc.perform(get("/api/v1/auction-rooms/410"))
			.andExpect(status().isGone())
			.andExpect(jsonPath("$.code").value("AUCTION_DELETED"));
	}

	private static AuctionRoomDetailResult result() {
		return new AuctionRoomDetailResult(
			Instant.parse("2026-07-21T10:59:30Z"),
			100L,
			"키크론 Q1 Pro 경매",
			AuctionStatus.WAITING,
			50_000L,
			Instant.parse("2026-07-21T11:00:00Z"),
			20,
			18,
			true,
			12,
			new AuctionRoomDetailResult.Product(
				501L,
				"키크론 Q1 Pro",
				"상품 설명",
				"ELECTRONICS",
				"https://cdn.example.com/products/501.png"
			),
			new AuctionRoomDetailResult.Seller("판**", null),
			true
		);
	}
}
