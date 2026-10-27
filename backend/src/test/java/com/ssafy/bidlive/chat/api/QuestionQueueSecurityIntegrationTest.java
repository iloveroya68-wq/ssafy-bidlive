package com.ssafy.bidlive.chat.api;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.ssafy.bidlive.chat.application.QuestionQueueCommandService;
import com.ssafy.bidlive.chat.application.QuestionQueuePage;
import com.ssafy.bidlive.chat.application.QuestionQueueQueryService;
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

/** 실제 보안 필터 체인에서 질문 큐 JWT 인증과 DELETE CSRF 예외를 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
class QuestionQueueSecurityIntegrationTest {

	private static final long AUCTION_ID = 42L;
	private static final long MEMBER_ID = 25L;
	private static final String COLLECTION_PATH =
		"/api/v1/auctions/42/questions";
	private static final String QUESTION_ID =
		"01J8W3P2N9KQ4T123456789ABC";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtTokenProvider jwtTokenProvider;

	@MockitoBean
	private QuestionQueueQueryService queryService;

	@MockitoBean
	private QuestionQueueCommandService commandService;

	@Test
	void allowsAuthenticatedQuestionRestore() throws Exception {
		when(queryService.getPendingQuestions(
			AUCTION_ID,
			MEMBER_ID,
			null,
			null
		)).thenReturn(new QuestionQueuePage(List.of(), null, false));

		mockMvc.perform(get(COLLECTION_PATH)
				.header(HttpHeaders.AUTHORIZATION, bearerAccessToken()))
			.andExpect(status().isOk());

		verify(queryService).getPendingQuestions(
			AUCTION_ID,
			MEMBER_ID,
			null,
			null
		);
	}

	@Test
	void allowsAuthenticatedResolutionWithoutCsrfToken() throws Exception {
		mockMvc.perform(delete(COLLECTION_PATH + "/" + QUESTION_ID)
				.header(HttpHeaders.AUTHORIZATION, bearerAccessToken()))
			.andExpect(status().isNoContent());

		verify(commandService).resolve(
			AUCTION_ID,
			MEMBER_ID,
			new ChatMessageId(QUESTION_ID)
		);
	}

	@Test
	void rejectsUnauthenticatedQuestionRestoreBeforeController() throws Exception {
		mockMvc.perform(get(COLLECTION_PATH))
			.andExpect(status().isUnauthorized());

		verifyNoInteractions(queryService);
	}

	@Test
	void rejectsUnauthenticatedResolutionBeforeController() throws Exception {
		mockMvc.perform(delete(COLLECTION_PATH + "/" + QUESTION_ID))
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
