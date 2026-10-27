package com.ssafy.bidlive.auction.api;

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
import com.ssafy.bidlive.auction.application.AuctionExtensionException;
import com.ssafy.bidlive.auction.application.AuctionExtensionResult;
import com.ssafy.bidlive.auction.application.AuctionExtensionService;
import com.ssafy.bidlive.auction.domain.AuctionExtensionErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** BID-03 성공 응답과 인증·연장 거부 사유별 HTTP 오류 매핑을 검증한다. */
class AuctionExtensionControllerTest {

	private static final String PATH = "/api/v1/auctions/100/extensions";

	private AuctionExtensionService extensionService;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		extensionService = mock(AuctionExtensionService.class);
		mockMvc = MockMvcBuilders.standaloneSetup(
			new AuctionExtensionController(extensionService)
		)
			.setControllerAdvice(new AuctionExtensionExceptionHandler())
			.setMessageConverters(new MappingJackson2HttpMessageConverter(
				Jackson2ObjectMapperBuilder.json()
					.featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
					.build()))
			.build();
		when(extensionService.extend(anyLong(), anyLong()))
			.thenReturn(new AuctionExtensionResult(
				100L,
				Instant.parse("2026-07-20T12:20:00Z"),
				Instant.parse("2026-07-20T12:25:00Z"),
				2,
				1
			));
	}

	@Test
	void returnsExtendedAuctionStateInKoreaTime() throws Exception {
		mockMvc.perform(post(PATH).principal(authenticated("25")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.auctionId").value(100))
			.andExpect(jsonPath("$.previousEndAt")
				.value("2026-07-20T21:20:00+09:00"))
			.andExpect(jsonPath("$.endAt").value("2026-07-20T21:25:00+09:00"))
			.andExpect(jsonPath("$.extensionCount").value(2))
			.andExpect(jsonPath("$.remainingExtensions").value(1));

		verify(extensionService).extend(100L, 25L);
	}

	@Test
	void rejectsMissingAuthenticationAsUnauthorized() throws Exception {
		mockMvc.perform(post(PATH))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	@Test
	void rejectsNonNumericPrincipalAsUnauthorized() throws Exception {
		mockMvc.perform(post(PATH).principal(authenticated("anonymousUser")))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	@Test
	void mapsExtensionFailuresToContractStatuses() throws Exception {
		assertMaps(AuctionExtensionErrorCode.NOT_AUCTION_SELLER, 403);
		assertMaps(AuctionExtensionErrorCode.AUCTION_NOT_FOUND, 404);
		assertMaps(AuctionExtensionErrorCode.AUCTION_NOT_LIVE, 409);
		assertMaps(AuctionExtensionErrorCode.EXTENSION_LIMIT_EXCEEDED, 409);
		assertMaps(AuctionExtensionErrorCode.EXTENSION_TOO_LATE, 409);
	}

	private void assertMaps(AuctionExtensionErrorCode errorCode, int expectedStatus)
		throws Exception {
		org.mockito.Mockito.doThrow(new AuctionExtensionException(errorCode))
			.when(extensionService).extend(anyLong(), anyLong());

		mockMvc.perform(post(PATH).principal(authenticated("25")))
			.andExpect(status().is(expectedStatus))
			.andExpect(jsonPath("$.code").value(errorCode.name()));
	}

	private static Principal authenticated(String name) {
		return new UsernamePasswordAuthenticationToken(name, "n/a", java.util.List.of());
	}
}
