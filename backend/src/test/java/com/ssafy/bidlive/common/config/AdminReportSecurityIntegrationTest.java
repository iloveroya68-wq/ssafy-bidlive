package com.ssafy.bidlive.common.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.ssafy.bidlive.common.security.JwtTokenProvider;
import com.ssafy.bidlive.member.domain.Member;
import com.ssafy.bidlive.member.domain.MemberRole;
import com.ssafy.bidlive.report.application.AdminReportQueryService;
import com.ssafy.bidlive.report.application.ReportedAuctionPage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 실제 필터 체인에서 ADMIN 게이트를 잠근다.
 *
 * <p>여기가 새면 아무 회원이나 신고 목록 전체를 읽는다 — 권한 판정은 `SecurityConfig`에만 있어
 * 단위 테스트로는 절대 못 잡는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminReportSecurityIntegrationTest {

	private static final String PATH = "/api/v1/admin/reports";
	private static final long MEMBER_ID = 25L;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtTokenProvider jwtTokenProvider;

	@MockitoBean
	private AdminReportQueryService queryService;

	@Test
	void allowsAdmin() throws Exception {
		when(queryService.getReportedAuctions(anyBoolean(), any(), anyInt()))
			.thenReturn(new ReportedAuctionPage(List.of(), null, false));

		mockMvc.perform(get(PATH)
			.header(HttpHeaders.AUTHORIZATION, bearer(MemberRole.ADMIN)))
			.andExpect(status().isOk());
	}

	@Test
	void rejectsAuthenticatedUserAsForbidden() throws Exception {
		mockMvc.perform(get(PATH)
			.header(HttpHeaders.AUTHORIZATION, bearer(MemberRole.USER)))
			.andExpect(status().isForbidden());

		verifyNoInteractions(queryService);
	}

	@Test
	void rejectsUnauthenticatedAsUnauthorized() throws Exception {
		mockMvc.perform(get(PATH))
			.andExpect(status().isUnauthorized());

		verifyNoInteractions(queryService);
	}

	@Test
	void rejectsMalformedCursorAsBadRequest() throws Exception {
		mockMvc.perform(get(PATH)
			.param("cursor", "not-a-cursor")
			.header(HttpHeaders.AUTHORIZATION, bearer(MemberRole.ADMIN)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_CURSOR"));

		verifyNoInteractions(queryService);
	}

	/**
	 * `size`를 `int`로 바인딩하면 Spring 타입 변환이 먼저 터져 이 봉투를 벗어난다 —
	 * 상태만 보면 그때도 400이라 통과해 버리므로 `code`까지 봐야 되돌림을 잡는다.
	 */
	@Test
	void rejectsNonNumericSizeAsBadRequest() throws Exception {
		mockMvc.perform(get(PATH)
			.param("size", "abc")
			.header(HttpHeaders.AUTHORIZATION, bearer(MemberRole.ADMIN)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_SIZE"));

		verifyNoInteractions(queryService);
	}

	/** `boolean`으로 바인딩하면 Spring 타입 변환이 먼저 터져 코드도 메시지도 없는 빈 400이 나간다. */
	@Test
	void rejectsNonBooleanIncludeEndedAsBadRequest() throws Exception {
		mockMvc.perform(get(PATH)
			.param("includeEnded", "abc")
			.header(HttpHeaders.AUTHORIZATION, bearer(MemberRole.ADMIN)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_INCLUDE_ENDED"));

		verifyNoInteractions(queryService);
	}

	/** 매처를 `HttpMethod.GET`으로 좁히면 HEAD가 `anyRequest().permitAll()`로 새어 인증 없이 쿼리가 돈다. */
	@Test
	void rejectsUnauthenticatedHeadAsUnauthorized() throws Exception {
		mockMvc.perform(head(PATH))
			.andExpect(status().isUnauthorized());

		verifyNoInteractions(queryService);
	}

	private String bearer(MemberRole role) {
		Member member = mock(Member.class);
		when(member.getId()).thenReturn(MEMBER_ID);
		when(member.getRole()).thenReturn(role);
		return "Bearer " + jwtTokenProvider.issue(member).accessToken();
	}
}
