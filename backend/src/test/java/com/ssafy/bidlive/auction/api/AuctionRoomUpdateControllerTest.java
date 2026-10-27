package com.ssafy.bidlive.auction.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.Principal;
import java.time.Instant;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.ssafy.bidlive.auction.application.AuctionRoomUpdateCommand;
import com.ssafy.bidlive.auction.application.AuctionRoomUpdateException;
import com.ssafy.bidlive.auction.application.AuctionRoomUpdateResult;
import com.ssafy.bidlive.auction.application.AuctionRoomUpdateService;
import com.ssafy.bidlive.auction.domain.AuctionRoomUpdateErrorCode;
import com.ssafy.bidlive.auction.domain.AuctionStartType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** 부분 수정 요청 매핑과 오류 코드별 HTTP 상태를 검증한다. */
class AuctionRoomUpdateControllerTest {

	private static final String PATH = "/api/v1/auction-rooms/100";

	private AuctionRoomUpdateService updateService;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		updateService = mock(AuctionRoomUpdateService.class);
		mockMvc = MockMvcBuilders.standaloneSetup(
			new AuctionRoomUpdateController(updateService)
		)
			.setControllerAdvice(new AuctionRoomUpdateExceptionHandler())
			.setMessageConverters(new MappingJackson2HttpMessageConverter(
				Jackson2ObjectMapperBuilder.json()
					.featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
					.build()))
			.build();
		when(updateService.updateRoom(any())).thenReturn(result());
	}

	@Test
	void returnsResolvedValuesAfterUpdate() throws Exception {
		mockMvc.perform(patch(PATH)
				.principal(authenticated("25"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"product":{"title":"바뀐 제목"},"auction":{"reservePrice":55000}}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.auctionId").value(100))
			.andExpect(jsonPath("$.productId").value(501))
			.andExpect(jsonPath("$.status").value("WAITING"))
			.andExpect(jsonPath("$.product.title").value("바뀐 제목"))
			.andExpect(jsonPath("$.product.category").value("ELECTRONICS"))
			.andExpect(jsonPath("$.auction.reservePrice").value(55000))
			.andExpect(jsonPath("$.auction.startType").value("SCHEDULED"))
			.andExpect(jsonPath("$.auction.scheduledAt").value("2026-07-22T06:00:00+09:00"))
			.andExpect(jsonPath("$.updatedAt").value("2026-07-21T19:00:00+09:00"));
	}

	@Test
	void passesOnlySentFieldsToService() throws Exception {
		mockMvc.perform(patch(PATH)
				.principal(authenticated("25"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"auction":{"startType":"IMMEDIATE"}}
					"""))
			.andExpect(status().isOk());

		ArgumentCaptor<AuctionRoomUpdateCommand> captor =
			ArgumentCaptor.forClass(AuctionRoomUpdateCommand.class);
		verify(updateService).updateRoom(captor.capture());
		AuctionRoomUpdateCommand command = captor.getValue();
		assertSent(command);
	}

	private static void assertSent(AuctionRoomUpdateCommand command) {
		org.assertj.core.api.Assertions.assertThat(command.auctionId()).isEqualTo(100L);
		org.assertj.core.api.Assertions.assertThat(command.memberId()).isEqualTo(25L);
		org.assertj.core.api.Assertions.assertThat(command.startType()).isEqualTo("IMMEDIATE");
		// 보내지 않은 값은 전부 null이어야 한다 — 빈 문자열이나 0으로 채우면 "유지"가 깨진다
		org.assertj.core.api.Assertions.assertThat(command.title()).isNull();
		org.assertj.core.api.Assertions.assertThat(command.description()).isNull();
		org.assertj.core.api.Assertions.assertThat(command.categoryCode()).isNull();
		org.assertj.core.api.Assertions.assertThat(command.imageUrl()).isNull();
		org.assertj.core.api.Assertions.assertThat(command.reservePrice()).isNull();
		org.assertj.core.api.Assertions.assertThat(command.durationMinutes()).isNull();
		org.assertj.core.api.Assertions.assertThat(command.scheduledAt()).isNull();
	}

	@Test
	void treatsMissingBodyAsEmptyRequest() throws Exception {
		when(updateService.updateRoom(any())).thenThrow(
			new AuctionRoomUpdateException(AuctionRoomUpdateErrorCode.EMPTY_UPDATE_REQUEST)
		);

		mockMvc.perform(patch(PATH).principal(authenticated("25")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("EMPTY_UPDATE_REQUEST"));
	}

	/** 깨진 JSON·해석 불가한 시각은 500이 아니라 400이어야 한다 */
	@Test
	void mapsUnreadableBodyToBadRequest() throws Exception {
		mockMvc.perform(patch(PATH)
				.principal(authenticated("25"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"auction\":{\"scheduledAt\":\"내일\"}}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_AUCTION_REQUEST"));
	}

	@Test
	void rejectsNonNumericPrincipalAsUnauthorized() throws Exception {
		mockMvc.perform(patch(PATH)
				.principal(authenticated("anonymousUser"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"product\":{\"title\":\"t\"}}"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	@Test
	void mapsErrorCodesToStatuses() throws Exception {
		assertMaps(AuctionRoomUpdateErrorCode.INVALID_AUCTION_REQUEST, 400);
		assertMaps(AuctionRoomUpdateErrorCode.NOT_AUCTION_SELLER, 403);
		assertMaps(AuctionRoomUpdateErrorCode.AUCTION_NOT_FOUND, 404);
		assertMaps(AuctionRoomUpdateErrorCode.AUCTION_UPDATE_NOT_ALLOWED, 409);
	}

	private void assertMaps(AuctionRoomUpdateErrorCode errorCode, int expectedStatus)
		throws Exception {
		org.mockito.Mockito.doThrow(new AuctionRoomUpdateException(errorCode))
			.when(updateService).updateRoom(any());

		mockMvc.perform(patch(PATH)
				.principal(authenticated("25"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"product\":{\"title\":\"t\"}}"))
			.andExpect(status().is(expectedStatus))
			.andExpect(jsonPath("$.code").value(errorCode.name()));
	}

	private static AuctionRoomUpdateResult result() {
		return new AuctionRoomUpdateResult(
			100L,
			501L,
			"WAITING",
			"바뀐 제목",
			"바뀐 설명",
			"ELECTRONICS",
			"https://cdn.example.com/products/changed.jpg",
			55_000L,
			30,
			AuctionStartType.SCHEDULED,
			Instant.parse("2026-07-21T21:00:00Z"),
			Instant.parse("2026-07-21T10:00:00Z")
		);
	}

	private static Principal authenticated(String name) {
		return new UsernamePasswordAuthenticationToken(name, "n/a", java.util.List.of());
	}
}
