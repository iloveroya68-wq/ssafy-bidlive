package com.ssafy.bidlive.chat.api;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.ssafy.bidlive.chat.application.ChatHistoryPage;
import com.ssafy.bidlive.chat.application.ChatHistoryQueryService;
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

/** 실제 보안 필터 체인에서 채팅 이력 API의 JWT 인증 경로를 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
class ChatHistorySecurityIntegrationTest {

	private static final String PATH = "/api/v1/auctions/42/chats";
	private static final long MEMBER_ID = 25L;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtTokenProvider jwtTokenProvider;

	@MockitoBean
	private ChatHistoryQueryService queryService;

	@Test
	void allowsAuthenticatedHistoryQuery() throws Exception {
		when(queryService.getHistory(42L, MEMBER_ID, null, null))
			.thenReturn(new ChatHistoryPage(List.of(), false));

		mockMvc.perform(get(PATH)
				.header(HttpHeaders.AUTHORIZATION, bearerAccessToken()))
			.andExpect(status().isOk());

		verify(queryService).getHistory(42L, MEMBER_ID, null, null);
	}

	@Test
	void rejectsUnauthenticatedHistoryQueryBeforeController() throws Exception {
		mockMvc.perform(get(PATH))
			.andExpect(status().isUnauthorized());

		verifyNoInteractions(queryService);
	}

	private String bearerAccessToken() {
		Member member = mock(Member.class);
		when(member.getId()).thenReturn(MEMBER_ID);
		when(member.getRole()).thenReturn(MemberRole.USER);
		return "Bearer " + jwtTokenProvider.issue(member).accessToken();
	}
}
