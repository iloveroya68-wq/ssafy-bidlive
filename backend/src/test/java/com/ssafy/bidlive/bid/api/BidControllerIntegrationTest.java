package com.ssafy.bidlive.bid.api;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.stream.Stream;

import com.ssafy.bidlive.bid.application.BidCommandService;
import com.ssafy.bidlive.bid.application.BidRejectedException;
import com.ssafy.bidlive.bid.application.BidResult;
import com.ssafy.bidlive.bid.domain.BidFailureCode;
import com.ssafy.bidlive.common.security.JwtTokenProvider;
import com.ssafy.bidlive.member.domain.Member;
import com.ssafy.bidlive.member.domain.MemberRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 실제 Security 필터 체인에서 BID-06 성공·검증·인증·비즈니스 오류 계약을 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
class BidControllerIntegrationTest {

	private static final String PATH = "/api/v1/auctions/100/bids";
	private static final UUID IDEMPOTENCY_KEY =
		UUID.fromString("9a61c1e4-7ad4-4e7c-83b5-a63edc00ac20");
	private static final OffsetDateTime SUBMITTED_AT =
		OffsetDateTime.parse("2026-07-20T21:12:44+09:00");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtTokenProvider jwtTokenProvider;

	@MockitoBean
	private BidCommandService bidCommandService;

	@Test
	void createsBidWithAuthenticatedMemberAndIdempotencyKey() throws Exception {
		when(bidCommandService.createBid(100L, 25L, 82_000L, IDEMPOTENCY_KEY))
			.thenReturn(acceptedResult());

		mockMvc.perform(post(PATH)
			.header(HttpHeaders.AUTHORIZATION, bearerAccessToken())
			.header("Idempotency-Key", IDEMPOTENCY_KEY)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"amount\":82000}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.auctionId").value(100L))
			.andExpect(jsonPath("$.myBidAmount").value(82_000L))
			.andExpect(jsonPath("$.previousBidAmount").value(70_000L))
			.andExpect(jsonPath("$.additionalHoldAmount").value(12_000L))
			.andExpect(jsonPath("$.totalHeldAmount").value(82_000L))
			.andExpect(jsonPath("$.availableBalance").value(118_000L))
			.andExpect(jsonPath("$.submittedAt").value(SUBMITTED_AT.toString()));

		verify(bidCommandService).createBid(100L, 25L, 82_000L, IDEMPOTENCY_KEY);
	}

	@Test
	void rejectsUnauthenticatedBidRequest() throws Exception {
		mockMvc.perform(post(PATH)
			.header("Idempotency-Key", IDEMPOTENCY_KEY)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"amount\":82000}"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void rejectsInvalidBidAmountWithSpecifiedErrorCode() throws Exception {
		mockMvc.perform(post(PATH)
			.header(HttpHeaders.AUTHORIZATION, bearerAccessToken())
			.header("Idempotency-Key", IDEMPOTENCY_KEY)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"amount\":0}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_BID_AMOUNT"));
	}

	@ParameterizedTest
	@MethodSource("rejectedBidCases")
	void mapsAtomicBidFailureToApiError(BidFailureCode failureCode, int expectedStatus) throws Exception {
		when(bidCommandService.createBid(eq(100L), eq(25L), eq(82_000L), any(UUID.class)))
			.thenThrow(new BidRejectedException(failureCode));

		mockMvc.perform(post(PATH)
			.header(HttpHeaders.AUTHORIZATION, bearerAccessToken())
			.header("Idempotency-Key", UUID.randomUUID())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"amount\":82000}"))
			.andExpect(status().is(expectedStatus))
			.andExpect(jsonPath("$.code").value(failureCode.name()));
	}

	@Test
	void allowsIdempotencyKeyInCorsPreflight() throws Exception {
		mockMvc.perform(options(PATH)
			.header(HttpHeaders.ORIGIN, "http://localhost:5173")
			.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
			.header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization,Idempotency-Key"))
			.andExpect(status().isOk())
			.andExpect(header().string(
				HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
				containsString("Idempotency-Key")
			));
	}

	private String bearerAccessToken() {
		Member member = mock(Member.class);
		when(member.getId()).thenReturn(25L);
		when(member.getRole()).thenReturn(MemberRole.USER);
		return "Bearer " + jwtTokenProvider.issue(member).accessToken();
	}

	private BidResult acceptedResult() {
		return new BidResult(
			100L,
			82_000L,
			70_000L,
			12_000L,
			82_000L,
			118_000L,
			SUBMITTED_AT
		);
	}

	private static Stream<Arguments> rejectedBidCases() {
		return Stream.of(
			Arguments.of(BidFailureCode.SELLER_CANNOT_BID, 403),
			Arguments.of(BidFailureCode.AUCTION_NOT_FOUND, 404),
			Arguments.of(BidFailureCode.AUCTION_NOT_LIVE, 409),
			Arguments.of(BidFailureCode.AUCTION_ALREADY_ENDED, 409),
			Arguments.of(BidFailureCode.BID_BELOW_RESERVE_PRICE, 409),
			Arguments.of(BidFailureCode.BID_NOT_HIGHER_THAN_PREVIOUS, 409),
			Arguments.of(BidFailureCode.PREDICTION_ALREADY_JOINED, 409),
			Arguments.of(BidFailureCode.IDEMPOTENCY_KEY_REUSED, 409),
			Arguments.of(BidFailureCode.DUPLICATE_REQUEST, 409),
			Arguments.of(BidFailureCode.INSUFFICIENT_AVAILABLE_BALANCE, 422)
		);
	}
}
