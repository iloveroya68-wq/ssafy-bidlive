package com.ssafy.bidlive.common.api;

import static com.ssafy.bidlive.common.application.RealtimeAuctionStateQueryException.Reason.AUCTION_NOT_FOUND;
import static com.ssafy.bidlive.common.application.RealtimeAuctionStateQueryException.Reason.AUCTION_STATE_UNAVAILABLE;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.List;

import com.ssafy.bidlive.common.application.RealtimeAuctionStateQueryException;
import com.ssafy.bidlive.common.application.RealtimeAuctionStateQueryService;
import com.ssafy.bidlive.common.application.RealtimeAuctionStateResult;
import com.ssafy.bidlive.common.application.RealtimeAuctionStateResult.ParticipationType;
import com.ssafy.bidlive.common.application.RealtimeAuctionStateResult.PinnedNotice;
import com.ssafy.bidlive.common.application.RealtimeAuctionStateSnapshot.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** realtime-state REST 경로의 익명·인증 응답과 안정적인 오류 매핑을 통합 검증합니다. */
class RealtimeAuctionStateApiIntegrationTest {
	private static final OffsetDateTime MUTED_UNTIL =
		OffsetDateTime.parse("2026-08-02T12:10:00Z");

	private RealtimeAuctionStateQueryService queryService;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		queryService = org.mockito.Mockito.mock(
			RealtimeAuctionStateQueryService.class
		);
		mockMvc = MockMvcBuilders.standaloneSetup(
			new RealtimeAuctionStateController(queryService)
		)
			.setControllerAdvice(new RealtimeAuctionStateExceptionHandler())
			.build();
	}

	@Test
	void returnsAuthenticatedMembersServerAuthoritySnapshot() throws Exception {
		when(queryService.getState(42L, 7L)).thenReturn(result(
			52_000L,
			ParticipationType.BID,
			true
		));
		var authentication = UsernamePasswordAuthenticationToken.authenticated(
			"7",
			"token",
			List.of()
		);

		mockMvc.perform(get("/api/v1/auctions/42/realtime-state")
				.principal(authentication))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.stateVersion").value(184L))
			.andExpect(jsonPath("$.auctionStatus").value("LIVE"))
			.andExpect(jsonPath("$.remainingExtensions").value(2))
			.andExpect(jsonPath("$.bidParticipantCount").value(5L))
			.andExpect(jsonPath("$.myBidAmount").value(52_000L))
			.andExpect(jsonPath("$.myParticipationType").value("BID"))
			.andExpect(jsonPath("$.pinnedNotice.messageId")
				.value("01J8W3P2N9KQ4T"))
			.andExpect(jsonPath("$.amIMuted").value(true))
			.andExpect(jsonPath("$.mutedUntil").exists())
			.andExpect(jsonPath("$.predictionBuckets").doesNotExist());

		verify(queryService).getState(42L, 7L);
	}

	@Test
	void allowsAnonymousSnapshotWithNoPersonalValues() throws Exception {
		when(queryService.getState(42L, null)).thenReturn(result(
			null,
			ParticipationType.NONE,
			false
		));

		mockMvc.perform(get("/api/v1/auctions/42/realtime-state"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.myBidAmount").doesNotExist())
			.andExpect(jsonPath("$.myParticipationType").value("NONE"))
			.andExpect(jsonPath("$.remainingExtensions").value(2))
			.andExpect(jsonPath("$.amIMuted").value(false))
			.andExpect(jsonPath("$.mutedUntil").doesNotExist());

		verify(queryService).getState(42L, null);
	}

	@Test
	void mapsMissingAuctionToNotFound() throws Exception {
		when(queryService.getState(42L, null)).thenThrow(
			new RealtimeAuctionStateQueryException(AUCTION_NOT_FOUND)
		);

		mockMvc.perform(get("/api/v1/auctions/42/realtime-state"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("AUCTION_NOT_FOUND"))
			.andExpect(jsonPath("$.status").value(404));
	}

	@Test
	void mapsIncompleteOrUnavailableStateToServiceUnavailable() throws Exception {
		when(queryService.getState(42L, null)).thenThrow(
			new RealtimeAuctionStateQueryException(AUCTION_STATE_UNAVAILABLE)
		);

		mockMvc.perform(get("/api/v1/auctions/42/realtime-state"))
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.code").value("AUCTION_STATE_UNAVAILABLE"))
			.andExpect(jsonPath("$.status").value(503));
	}

	private static RealtimeAuctionStateResult result(
		Long myBidAmount,
		ParticipationType participationType,
		boolean amIMuted
	) {
		return new RealtimeAuctionStateResult(
			184L,
			Status.LIVE,
			OffsetDateTime.parse("2026-08-02T12:07:11Z"),
			431L,
			1,
			2,
			7L,
			5L,
			23L,
			myBidAmount,
			null,
			null,
			participationType,
			new PinnedNotice("01J8W3P2N9KQ4T", "택배비 포함가입니다"),
			amIMuted,
			amIMuted ? MUTED_UNTIL : null
		);
	}
}
