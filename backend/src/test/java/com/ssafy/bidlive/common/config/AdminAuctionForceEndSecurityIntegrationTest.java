package com.ssafy.bidlive.common.config;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import com.ssafy.bidlive.auction.application.AuctionForceEndResult;
import com.ssafy.bidlive.auction.application.AuctionForceEndService;
import com.ssafy.bidlive.common.security.JwtTokenProvider;
import com.ssafy.bidlive.member.domain.Member;
import com.ssafy.bidlive.member.domain.MemberRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * DEAL-09가 이 레포에서 <b>유일하게 역할까지 보는 경로</b>라, 실제 필터 체인에서 그 게이트를 잠근다.
 *
 * <p>`hasRole("ADMIN")`이 새면 아무 회원이나 남의 경매를 끝내고 환급까지 돌릴 수 있다.
 * 단위 테스트로는 절대 못 잡는 자리다 — 권한 판정은 `SecurityConfig`에만 있다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminAuctionForceEndSecurityIntegrationTest {

	private static final String PATH = "/api/v1/admin/auctions/42/force-end";
	private static final String BODY =
		"{\"reason\":\"POLICY_VIOLATION\",\"adminNote\":\"금지 품목 신고 확인\"}";
	private static final long MEMBER_ID = 25L;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtTokenProvider jwtTokenProvider;

	@MockitoBean
	private AuctionForceEndService forceEndService;

	@Test
	void allowsAdminWithoutCsrfToken() throws Exception {
		when(forceEndService.forceEnd(anyLong(), anyString()))
			.thenReturn(new AuctionForceEndResult(
				42L, Instant.parse("2026-07-31T06:00:00Z"), 0, 0L, 0
			));

		mockMvc.perform(post(PATH)
			.header(HttpHeaders.AUTHORIZATION, bearerAccessToken(MemberRole.ADMIN))
			.contentType(MediaType.APPLICATION_JSON)
			.content(BODY))
			.andExpect(status().isOk());

		verify(forceEndService).forceEnd(42L, "POLICY_VIOLATION");
	}

	@Test
	void rejectsAuthenticatedUserAsForbiddenBeforeController() throws Exception {
		mockMvc.perform(post(PATH)
			.header(HttpHeaders.AUTHORIZATION, bearerAccessToken(MemberRole.USER))
			.contentType(MediaType.APPLICATION_JSON)
			.content(BODY))
			.andExpect(status().isForbidden());

		verifyNoInteractions(forceEndService);
	}

	@Test
	void rejectsUnauthenticatedCallAsUnauthorized() throws Exception {
		mockMvc.perform(post(PATH)
			.contentType(MediaType.APPLICATION_JSON)
			.content(BODY))
			.andExpect(status().isUnauthorized());

		verifyNoInteractions(forceEndService);
	}

	private String bearerAccessToken(MemberRole role) {
		Member member = mock(Member.class);
		when(member.getId()).thenReturn(MEMBER_ID);
		when(member.getRole()).thenReturn(role);
		return "Bearer " + jwtTokenProvider.issue(member).accessToken();
	}
}
