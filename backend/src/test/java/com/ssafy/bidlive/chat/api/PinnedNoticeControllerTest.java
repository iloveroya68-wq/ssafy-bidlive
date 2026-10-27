package com.ssafy.bidlive.chat.api;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ssafy.bidlive.chat.application.PinnedNoticeCommandService;
import com.ssafy.bidlive.chat.application.PinnedNoticeException;
import com.ssafy.bidlive.chat.domain.ChatMessageId;
import com.ssafy.bidlive.chat.domain.PinnedNotice;
import com.ssafy.bidlive.chat.domain.PinnedNoticeErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** CHAT-02 REST 응답·인증·오류 상태 매핑을 검증합니다. */
class PinnedNoticeControllerTest {

	private static final String MESSAGE_ID =
		"01J8W3P2N9KQ4T123456789ABC";

	private PinnedNoticeCommandService commandService;
	private MockMvc mockMvc;
	private UsernamePasswordAuthenticationToken authentication;

	@BeforeEach
	void setUp() {
		commandService = org.mockito.Mockito.mock(PinnedNoticeCommandService.class);
		mockMvc = MockMvcBuilders.standaloneSetup(
			new PinnedNoticeController(commandService)
		)
			.setControllerAdvice(new PinnedNoticeExceptionHandler())
			.build();
		authentication = UsernamePasswordAuthenticationToken.authenticated(
			"7",
			"token",
			java.util.List.of()
		);
	}

	@Test
	void pinsNoticeAndReturnsMessageAsString() throws Exception {
		when(commandService.pin(42L, 7L, new ChatMessageId(MESSAGE_ID)))
			.thenReturn(new PinnedNotice(MESSAGE_ID, "배송비 포함입니다."));

		mockMvc.perform(post("/api/v1/auctions/42/chat/pin")
				.principal(authentication)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"messageId\":\"" + MESSAGE_ID + "\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.messageId").value(MESSAGE_ID))
			.andExpect(jsonPath("$.content").value("배송비 포함입니다."));

		verify(commandService).pin(42L, 7L, new ChatMessageId(MESSAGE_ID));
	}

	@Test
	void unpinsIdempotentlyWithNoContent() throws Exception {
		mockMvc.perform(delete("/api/v1/auctions/42/chat/pin")
				.principal(authentication))
			.andExpect(status().isNoContent());

		verify(commandService).unpin(42L, 7L);
	}

	@Test
	void rejectsMissingAuthentication() throws Exception {
		mockMvc.perform(post("/api/v1/auctions/42/chat/pin")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"messageId\":\"" + MESSAGE_ID + "\"}"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	@Test
	void rejectsInvalidIdentifiers() throws Exception {
		mockMvc.perform(post("/api/v1/auctions/not-number/chat/pin")
				.principal(authentication)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"messageId\":\"" + MESSAGE_ID + "\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_AUCTION_ID"));

		mockMvc.perform(post("/api/v1/auctions/42/chat/pin")
				.principal(authentication)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"messageId\":\"not-ulid\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_MESSAGE_ID"));
	}

	@Test
	void mapsDomainFailuresToStableErrors() throws Exception {
		doThrow(new PinnedNoticeException(PinnedNoticeErrorCode.NOT_AUCTION_HOST))
			.when(commandService).unpin(42L, 7L);

		mockMvc.perform(delete("/api/v1/auctions/42/chat/pin")
				.principal(authentication))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("NOT_AUCTION_HOST"));
	}
}
