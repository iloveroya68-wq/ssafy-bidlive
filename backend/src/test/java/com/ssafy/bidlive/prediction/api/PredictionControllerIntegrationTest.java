package com.ssafy.bidlive.prediction.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.stream.Stream;

import com.ssafy.bidlive.common.security.JwtTokenProvider;
import com.ssafy.bidlive.member.domain.Member;
import com.ssafy.bidlive.member.domain.MemberRole;
import com.ssafy.bidlive.prediction.application.PredictionCommandService;
import com.ssafy.bidlive.prediction.application.PredictionCreateResult;
import com.ssafy.bidlive.prediction.application.PredictionRejectedException;
import com.ssafy.bidlive.prediction.domain.PredictionParticipationFailureCode;
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

/** 실제 Security 필터 체인에서 PRED-01 성공·검증·오류 계약을 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
class PredictionControllerIntegrationTest {

	private static final String PATH =
		"/api/v1/auctions/100/predictions";
	private static final UUID CLIENT_REQUEST_ID =
		UUID.fromString("2b8bb687-6d5e-4e09-b651-b88727266a51");
	private static final OffsetDateTime SUBMITTED_AT =
		OffsetDateTime.parse("2026-07-21T20:08:00+09:00");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtTokenProvider jwtTokenProvider;

	@MockitoBean
	private PredictionCommandService commandService;

	@Test
	void createsPredictionForAuthenticatedMember() throws Exception {
		when(commandService.createPrediction(
			100L,
			25L,
			74_000L,
			300L,
			CLIENT_REQUEST_ID
		)).thenReturn(new PredictionCreateResult(
			701L,
			100L,
			74_000L,
			300L,
			SUBMITTED_AT
		));

		mockMvc.perform(post(PATH)
			.header(HttpHeaders.AUTHORIZATION, bearerAccessToken())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
				{
				  "predictedPrice": 74000,
				  "stakeAmount": 300,
				  "clientRequestId": "%s"
				}
				""".formatted(CLIENT_REQUEST_ID)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.predictionId").value(701L))
			.andExpect(jsonPath("$.auctionId").value(100L))
			.andExpect(jsonPath("$.predictedPrice").value(74_000L))
			.andExpect(jsonPath("$.stakeAmount").value(300L))
			.andExpect(jsonPath("$.status").value("PLACED"))
			.andExpect(jsonPath("$.submittedAt")
				.value("2026-07-21T20:08:00+09:00"));

		verify(commandService).createPrediction(
			100L,
			25L,
			74_000L,
			300L,
			CLIENT_REQUEST_ID
		);
	}

	@Test
	void rejectsUnauthenticatedPredictionRequest() throws Exception {
		mockMvc.perform(post(PATH)
			.contentType(MediaType.APPLICATION_JSON)
			.content(validRequest()))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void rejectsInvalidPredictedPrice() throws Exception {
		mockMvc.perform(post(PATH)
			.header(HttpHeaders.AUTHORIZATION, bearerAccessToken())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
				{
				  "predictedPrice": 0,
				  "stakeAmount": 300,
				  "clientRequestId": "%s"
				}
				""".formatted(CLIENT_REQUEST_ID)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code")
				.value("INVALID_PREDICTED_PRICE"));
	}

	@Test
	void rejectsInvalidStakeAmount() throws Exception {
		mockMvc.perform(post(PATH)
			.header(HttpHeaders.AUTHORIZATION, bearerAccessToken())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
				{
				  "predictedPrice": 74000,
				  "stakeAmount": 0,
				  "clientRequestId": "%s"
				}
				""".formatted(CLIENT_REQUEST_ID)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_STAKE_AMOUNT"));
	}

	@Test
	void rejectsInvalidClientRequestId() throws Exception {
		mockMvc.perform(post(PATH)
			.header(HttpHeaders.AUTHORIZATION, bearerAccessToken())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
				{
				  "predictedPrice": 74000,
				  "stakeAmount": 300,
				  "clientRequestId": "not-a-uuid"
				}
				"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code")
				.value("INVALID_CLIENT_REQUEST_ID"));
	}

	@ParameterizedTest
	@MethodSource("rejectedPredictionCases")
	void mapsPredictionFailureToApiError(
		PredictionParticipationFailureCode failureCode,
		int expectedStatus
	) throws Exception {
		when(commandService.createPrediction(
			eq(100L),
			eq(25L),
			eq(74_000L),
			eq(300L),
			any(UUID.class)
		)).thenThrow(new PredictionRejectedException(failureCode));

		mockMvc.perform(post(PATH)
			.header(HttpHeaders.AUTHORIZATION, bearerAccessToken())
			.contentType(MediaType.APPLICATION_JSON)
			.content(validRequest()))
			.andExpect(status().is(expectedStatus))
			.andExpect(jsonPath("$.code").value(failureCode.name()));
	}

	private String bearerAccessToken() {
		Member member = mock(Member.class);
		when(member.getId()).thenReturn(25L);
		when(member.getRole()).thenReturn(MemberRole.USER);
		return "Bearer " + jwtTokenProvider.issue(member).accessToken();
	}

	private String validRequest() {
		return """
			{
			  "predictedPrice": 74000,
			  "stakeAmount": 300,
			  "clientRequestId": "%s"
			}
			""".formatted(CLIENT_REQUEST_ID);
	}

	private static Stream<Arguments> rejectedPredictionCases() {
		return Stream.of(
			Arguments.of(
				PredictionParticipationFailureCode.SELLER_CANNOT_PREDICT,
				403
			),
			Arguments.of(
				PredictionParticipationFailureCode.BIDDER_CANNOT_PREDICT,
				403
			),
			Arguments.of(
				PredictionParticipationFailureCode.AUCTION_NOT_FOUND,
				404
			),
			Arguments.of(
				PredictionParticipationFailureCode.AUCTION_NOT_LIVE,
				409
			),
			Arguments.of(
				PredictionParticipationFailureCode.PREDICTION_CLOSED,
				409
			),
			Arguments.of(
				PredictionParticipationFailureCode
					.PREDICTION_ALREADY_EXISTS,
				409
			),
			Arguments.of(
				PredictionParticipationFailureCode
					.INSUFFICIENT_BETTING_POINTS,
				409
			),
			Arguments.of(
				PredictionParticipationFailureCode.IDEMPOTENCY_KEY_REUSED,
				409
			)
		);
	}
}
