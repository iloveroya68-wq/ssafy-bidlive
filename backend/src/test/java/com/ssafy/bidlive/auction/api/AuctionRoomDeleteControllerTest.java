package com.ssafy.bidlive.auction.api;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.Principal;

import com.ssafy.bidlive.auction.application.AuctionRoomDeleteException;
import com.ssafy.bidlive.auction.application.AuctionRoomDeleteService;
import com.ssafy.bidlive.auction.domain.AuctionRoomDeleteErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** 삭제 성공 시 204와, 오류 코드별 HTTP 상태 매핑을 검증한다. */
class AuctionRoomDeleteControllerTest {

	private static final String PATH = "/api/v1/auction-rooms/100";

	private AuctionRoomDeleteService deleteService;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		deleteService = mock(AuctionRoomDeleteService.class);
		mockMvc = MockMvcBuilders.standaloneSetup(
			new AuctionRoomDeleteController(deleteService)
		)
			.setControllerAdvice(new AuctionRoomDeleteExceptionHandler())
			.build();
	}

	@Test
	void returnsNoContentWithAuthenticatedSeller() throws Exception {
		doNothing().when(deleteService).deleteRoom(anyLong(), anyLong());

		mockMvc.perform(delete(PATH).principal(authenticated("25")))
			.andExpect(status().isNoContent());

		verify(deleteService).deleteRoom(100L, 25L);
	}

	/**
	 * 익명 요청은 `AnonymousAuthenticationToken`으로 들어와 `isAuthenticated()`가 참이다.
	 * 이름이 숫자가 아니라는 점으로 걸러야 하고, 안 걸르면 `Long.parseLong`이 터져 500이 된다.
	 */
	@Test
	void rejectsNonNumericPrincipalAsUnauthorized() throws Exception {
		mockMvc.perform(delete(PATH).principal(authenticated("anonymousUser")))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	@Test
	void rejectsMissingPrincipalAsUnauthorized() throws Exception {
		mockMvc.perform(delete(PATH))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	@Test
	void mapsNotSellerToForbidden() throws Exception {
		givenServiceFails(AuctionRoomDeleteErrorCode.NOT_AUCTION_SELLER);

		mockMvc.perform(delete(PATH).principal(authenticated("25")))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("NOT_AUCTION_SELLER"));
	}

	@Test
	void mapsMissingRoomToNotFound() throws Exception {
		givenServiceFails(AuctionRoomDeleteErrorCode.AUCTION_NOT_FOUND);

		mockMvc.perform(delete(PATH).principal(authenticated("25")))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("AUCTION_NOT_FOUND"));
	}

	@Test
	void mapsAlreadyStartedToConflict() throws Exception {
		givenServiceFails(AuctionRoomDeleteErrorCode.AUCTION_ALREADY_STARTED);

		mockMvc.perform(delete(PATH).principal(authenticated("25")))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("AUCTION_ALREADY_STARTED"));
	}

	@Test
	void mapsExistingParticipationToConflict() throws Exception {
		givenServiceFails(AuctionRoomDeleteErrorCode.AUCTION_HAS_PARTICIPATION);

		mockMvc.perform(delete(PATH).principal(authenticated("25")))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("AUCTION_HAS_PARTICIPATION"));
	}

	private void givenServiceFails(AuctionRoomDeleteErrorCode errorCode) {
		doThrow(new AuctionRoomDeleteException(errorCode))
			.when(deleteService).deleteRoom(anyLong(), any());
	}

	private static Principal authenticated(String name) {
		Authentication authentication =
			new UsernamePasswordAuthenticationToken(name, "n/a", java.util.List.of());
		return authentication;
	}
}
