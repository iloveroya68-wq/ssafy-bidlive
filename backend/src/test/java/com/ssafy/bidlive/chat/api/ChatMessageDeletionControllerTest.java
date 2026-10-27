package com.ssafy.bidlive.chat.api;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ssafy.bidlive.chat.application.ChatMessageDeletionErrorCode;
import com.ssafy.bidlive.chat.application.ChatMessageDeletionException;
import com.ssafy.bidlive.chat.application.ChatMessageDeletionService;
import com.ssafy.bidlive.chat.domain.ChatMessageId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** 채팅 삭제 REST 입력과 안정적 오류 응답 매핑을 검증합니다. */
class ChatMessageDeletionControllerTest {

	private static final String MESSAGE_ID =
		"01J8W3P2N9KQ4T123456789ABC";
	private ChatMessageDeletionService deletionService;
	private MockMvc mockMvc;
	private UsernamePasswordAuthenticationToken authentication;

	@BeforeEach
	void setUp() {
		deletionService = mock(ChatMessageDeletionService.class);
		mockMvc = MockMvcBuilders.standaloneSetup(
			new ChatMessageDeletionController(deletionService)
		)
			.setControllerAdvice(new ChatMessageDeletionExceptionHandler())
			.build();
		authentication = UsernamePasswordAuthenticationToken.authenticated(
			"7",
			"token",
			java.util.List.of()
		);
	}

	@Test
	void deletesMessageIdempotentlyWithNoContent() throws Exception {
		mockMvc.perform(delete(
				"/api/v1/auctions/42/chats/{messageId}",
				MESSAGE_ID
			).principal(authentication))
			.andExpect(status().isNoContent());

		verify(deletionService).deleteByMember(
			42L,
			7L,
			new ChatMessageId(MESSAGE_ID)
		);
	}

	@Test
	void rejectsInvalidIdentifiersAndAuthentication() throws Exception {
		mockMvc.perform(delete(
				"/api/v1/auctions/not-number/chats/{messageId}",
				MESSAGE_ID
			).principal(authentication))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_AUCTION_ID"));

		mockMvc.perform(delete("/api/v1/auctions/42/chats/not-ulid")
				.principal(authentication))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_MESSAGE_ID"));

		mockMvc.perform(delete(
			"/api/v1/auctions/42/chats/{messageId}",
			MESSAGE_ID
		))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	@Test
	void mapsPermissionAndMissingFailures() throws Exception {
		doThrow(new ChatMessageDeletionException(
			ChatMessageDeletionErrorCode.CHAT_DELETE_FORBIDDEN
		)).when(deletionService).deleteByMember(
			42L,
			7L,
			new ChatMessageId(MESSAGE_ID)
		);

		mockMvc.perform(delete(
				"/api/v1/auctions/42/chats/{messageId}",
				MESSAGE_ID
			).principal(authentication))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("CHAT_DELETE_FORBIDDEN"));
	}
}
