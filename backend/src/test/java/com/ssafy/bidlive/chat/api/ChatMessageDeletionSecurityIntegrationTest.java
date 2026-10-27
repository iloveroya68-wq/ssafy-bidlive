package com.ssafy.bidlive.chat.api;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ssafy.bidlive.chat.application.ChatMessageDeletionService;
import com.ssafy.bidlive.chat.domain.ChatMessageId;
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

/** 실제 보안 필터에서 채팅 삭제 API의 JWT 인증과 CSRF 예외를 검증합니다. */
@SpringBootTest
@AutoConfigureMockMvc
class ChatMessageDeletionSecurityIntegrationTest {

	private static final String MESSAGE_ID =
		"01J8W3P2N9KQ4T123456789ABC";
	private static final String PATH =
		"/api/v1/auctions/42/chats/" + MESSAGE_ID;
	private static final long MEMBER_ID = 25L;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtTokenProvider jwtTokenProvider;

	@MockitoBean
	private ChatMessageDeletionService deletionService;

	@Test
	void allowsAuthenticatedDeletionWithoutCsrfToken() throws Exception {
		mockMvc.perform(delete(PATH).header(
			HttpHeaders.AUTHORIZATION,
			bearerAccessToken()
		)).andExpect(status().isNoContent());

		verify(deletionService).deleteByMember(
			42L,
			MEMBER_ID,
			new ChatMessageId(MESSAGE_ID)
		);
	}

	@Test
	void rejectsUnauthenticatedDeletionBeforeController() throws Exception {
		mockMvc.perform(delete(PATH)).andExpect(status().isUnauthorized());
		verifyNoInteractions(deletionService);
	}

	private String bearerAccessToken() {
		Member member = mock(Member.class);
		when(member.getId()).thenReturn(MEMBER_ID);
		when(member.getRole()).thenReturn(MemberRole.USER);
		return "Bearer " + jwtTokenProvider.issue(member).accessToken();
	}
}
