package com.ssafy.bidlive.common.config;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import com.ssafy.bidlive.auction.application.AuctionExtensionResult;
import com.ssafy.bidlive.auction.application.AuctionExtensionService;
import com.ssafy.bidlive.common.security.JwtTokenProvider;
import com.ssafy.bidlive.member.domain.Member;
import com.ssafy.bidlive.member.domain.MemberRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 실제 보안 필터 체인에서 BID-03의 JWT 인증과 CSRF 예외 정책을 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
class AuctionExtensionSecurityIntegrationTest {

	private static final String PATH = "/api/v1/auctions/100/extensions";
	private static final long MEMBER_ID = 25L;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtTokenProvider jwtTokenProvider;

	@MockitoBean
	private AuctionExtensionService extensionService;

	@Test
	void allowsAuthenticatedExtensionWithoutCsrfToken() throws Exception {
		when(extensionService.extend(100L, MEMBER_ID))
			.thenReturn(new AuctionExtensionResult(
				100L,
				Instant.parse("2026-07-20T12:20:00Z"),
				Instant.parse("2026-07-20T12:25:00Z"),
				2,
				1
			));

		mockMvc.perform(post(PATH)
			.header(HttpHeaders.AUTHORIZATION, bearerAccessToken()))
			.andExpect(status().isOk());

		verify(extensionService).extend(100L, MEMBER_ID);
	}

	@Test
	void rejectsUnauthenticatedExtensionBeforeController() throws Exception {
		mockMvc.perform(post(PATH))
			.andExpect(status().isUnauthorized());

		verifyNoInteractions(extensionService);
	}

	private String bearerAccessToken() {
		Member member = mock(Member.class);
		when(member.getId()).thenReturn(MEMBER_ID);
		when(member.getRole()).thenReturn(MemberRole.USER);
		return "Bearer " + jwtTokenProvider.issue(member).accessToken();
	}
}
