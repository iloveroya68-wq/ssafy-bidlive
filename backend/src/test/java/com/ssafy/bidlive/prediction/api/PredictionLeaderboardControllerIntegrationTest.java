package com.ssafy.bidlive.prediction.api;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import com.ssafy.bidlive.common.security.JwtTokenProvider;
import com.ssafy.bidlive.member.domain.Member;
import com.ssafy.bidlive.member.domain.MemberRole;
import com.ssafy.bidlive.prediction.application.PredictionLeaderboardEntry;
import com.ssafy.bidlive.prediction.application.PredictionLeaderboardQueryException;
import com.ssafy.bidlive.prediction.application.PredictionLeaderboardQueryResult;
import com.ssafy.bidlive.prediction.application.PredictionLeaderboardQueryService;
import com.ssafy.bidlive.prediction.domain.PredictionLeaderboardQueryErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 실제 Security 필터 체인에서 PRED-05 인증·조회·오류 응답 계약을 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
class PredictionLeaderboardControllerIntegrationTest {

	private static final String PATH =
		"/api/v1/predictions/leaderboards/daily";
	private static final LocalDate RANKING_DATE =
		LocalDate.parse("2026-07-22");
	private static final OffsetDateTime CALCULATED_AT =
		OffsetDateTime.parse("2026-07-23T00:05:03+09:00");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtTokenProvider jwtTokenProvider;

	@MockitoBean
	private PredictionLeaderboardQueryService queryService;

	@Test
	void returnsRequestedPageAndAuthenticatedMemberRank() throws Exception {
		when(queryService.query(
			RANKING_DATE,
			20,
			"opaque-cursor",
			25L
		)).thenReturn(new PredictionLeaderboardQueryResult(
			RANKING_DATE,
			CALCULATED_AT,
			List.of(entry(1L, 101L, "시세예언자", "400.0")),
			entry(18L, 25L, "택림", "200.0"),
			"next-cursor",
			true
		));

		mockMvc.perform(get(PATH)
			.queryParam("date", RANKING_DATE.toString())
			.queryParam("size", "20")
			.queryParam("cursor", "opaque-cursor")
			.header(HttpHeaders.AUTHORIZATION, bearerAccessToken()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.rankingDate").value("2026-07-22"))
			.andExpect(jsonPath("$.calculatedAt")
				.value("2026-07-23T00:05:03+09:00"))
			.andExpect(jsonPath("$.items[0].rank").value(1L))
			.andExpect(jsonPath("$.items[0].nickname")
				.value("시세예언자"))
			.andExpect(jsonPath("$.items[0].memberId").doesNotExist())
			.andExpect(jsonPath("$.items[0].settledPredictionCount")
				.value(3))
			.andExpect(jsonPath("$.items[0].totalStakedPoints")
				.value(1_500L))
			.andExpect(jsonPath("$.items[0].totalPayoutPoints")
				.value(3_000L))
			.andExpect(jsonPath("$.items[0].payoutRate").value(400.0))
			.andExpect(jsonPath("$.myRank.rank").value(18L))
			.andExpect(jsonPath("$.myRank.nickname").value("택림"))
			.andExpect(jsonPath("$.nextCursor").value("next-cursor"))
			.andExpect(jsonPath("$.hasNext").value(true));

		verify(queryService).query(
			RANKING_DATE,
			20,
			"opaque-cursor",
			25L
		);
	}

	@Test
	void appliesDefaultQueryConditions() throws Exception {
		when(queryService.query(null, 10, null, 25L))
			.thenReturn(new PredictionLeaderboardQueryResult(
				RANKING_DATE,
				null,
				List.of(),
				null,
				null,
				false
			));

		mockMvc.perform(get(PATH)
			.header(HttpHeaders.AUTHORIZATION, bearerAccessToken()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.rankingDate").value("2026-07-22"))
			.andExpect(jsonPath("$.calculatedAt").isEmpty())
			.andExpect(jsonPath("$.items").isEmpty())
			.andExpect(jsonPath("$.myRank").isEmpty())
			.andExpect(jsonPath("$.nextCursor").isEmpty())
			.andExpect(jsonPath("$.hasNext").value(false));

		verify(queryService).query(null, 10, null, 25L);
	}

	@Test
	void rejectsUnauthenticatedRequestBeforeQuery() throws Exception {
		mockMvc.perform(get(PATH))
			.andExpect(status().isUnauthorized());

		verifyNoInteractions(queryService);
	}

	@Test
	void rejectsMalformedRankingDate() throws Exception {
		mockMvc.perform(get(PATH)
			.queryParam("date", "2026-07-XX")
			.header(HttpHeaders.AUTHORIZATION, bearerAccessToken()))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_RANKING_DATE"));

		verifyNoInteractions(queryService);
	}

	@Test
	void rejectsPageSizeOutsideContractRange() throws Exception {
		mockMvc.perform(get(PATH)
			.queryParam("size", "101")
			.header(HttpHeaders.AUTHORIZATION, bearerAccessToken()))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_PAGE_SIZE"));

		verifyNoInteractions(queryService);
	}

	@ParameterizedTest
	@EnumSource(
		value = PredictionLeaderboardQueryErrorCode.class,
		names = {"INVALID_RANKING_DATE", "INVALID_CURSOR"}
	)
	void mapsServiceValidationFailure(
		PredictionLeaderboardQueryErrorCode errorCode
	) throws Exception {
		when(queryService.query(RANKING_DATE, 20, "cursor", 25L))
			.thenThrow(new PredictionLeaderboardQueryException(errorCode));

		mockMvc.perform(get(PATH)
			.queryParam("date", RANKING_DATE.toString())
			.queryParam("size", "20")
			.queryParam("cursor", "cursor")
			.header(HttpHeaders.AUTHORIZATION, bearerAccessToken()))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value(errorCode.name()));
	}

	private String bearerAccessToken() {
		Member member = mock(Member.class);
		when(member.getId()).thenReturn(25L);
		when(member.getRole()).thenReturn(MemberRole.USER);
		return "Bearer " + jwtTokenProvider.issue(member).accessToken();
	}

	private static PredictionLeaderboardEntry entry(
		long rank,
		long memberId,
		String nickname,
		String payoutRate
	) {
		return new PredictionLeaderboardEntry(
			rank,
			memberId,
			nickname,
			3,
			1_500L,
			3_000L,
			new BigDecimal(payoutRate)
		);
	}
}
