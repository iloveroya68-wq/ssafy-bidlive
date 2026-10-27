package com.ssafy.bidlive.common.config;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ssafy.bidlive.chat.application.PinnedNoticeCommandService;
import com.ssafy.bidlive.chat.domain.ChatMessageId;
import com.ssafy.bidlive.chat.domain.PinnedNotice;
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

/** 실제 보안 필터 체인에서 고정 공지 API의 JWT 인증과 CSRF 예외 정책을 검증합니다. */
@SpringBootTest
@AutoConfigureMockMvc
class PinnedNoticeSecurityIntegrationTest {

	private static final String PATH = "/api/v1/auctions/42/chat/pin";
	private static final long MEMBER_ID = 25L;
	private static final String MESSAGE_ID =
		"01J8W3P2N9KQ4T123456789ABC";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtTokenProvider jwtTokenProvider;

	@MockitoBean
	private PinnedNoticeCommandService commandService;

	@Test
	void allowsAuthenticatedPinWithoutCsrfToken() throws Exception {
		when(commandService.pin(
			42L,
			MEMBER_ID,
			new ChatMessageId(MESSAGE_ID)
		)).thenReturn(new PinnedNotice(MESSAGE_ID, "배송비 포함입니다."));

		mockMvc.perform(post(PATH)
			.header(HttpHeaders.AUTHORIZATION, bearerAccessToken())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"messageId\":\"" + MESSAGE_ID + "\"}"))
			.andExpect(status().isOk());

		verify(commandService).pin(
			42L,
			MEMBER_ID,
			new ChatMessageId(MESSAGE_ID)
		);
	}

	@Test
	void allowsAuthenticatedUnpinWithoutCsrfToken() throws Exception {
		mockMvc.perform(delete(PATH)
			.header(HttpHeaders.AUTHORIZATION, bearerAccessToken()))
			.andExpect(status().isNoContent());

		verify(commandService).unpin(42L, MEMBER_ID);
	}

	@Test
	void rejectsUnauthenticatedPinAsUnauthorizedBeforeController() throws Exception {
		mockMvc.perform(post(PATH)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"messageId\":\"" + MESSAGE_ID + "\"}"))
			.andExpect(status().isUnauthorized());

		verifyNoInteractions(commandService);
	}

	@Test
	void rejectsUnauthenticatedUnpinAsUnauthorizedBeforeController() throws Exception {
		mockMvc.perform(delete(PATH))
			.andExpect(status().isUnauthorized());

		verifyNoInteractions(commandService);
	}

	private String bearerAccessToken() {
		Member member = mock(Member.class);
		when(member.getId()).thenReturn(MEMBER_ID);
		when(member.getRole()).thenReturn(MemberRole.USER);
		return "Bearer " + jwtTokenProvider.issue(member).accessToken();
	}
}
