package com.ssafy.bidlive.chat.api;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ssafy.bidlive.chat.application.ChatHistoryErrorCode;
import com.ssafy.bidlive.chat.application.ChatHistoryException;
import com.ssafy.bidlive.chat.application.ChatHistoryPage;
import com.ssafy.bidlive.chat.application.ChatHistoryQueryService;
import com.ssafy.bidlive.chat.domain.ChatMessage;
import com.ssafy.bidlive.chat.domain.ChatMessageContent;
import com.ssafy.bidlive.chat.domain.ChatMessageId;
import com.ssafy.bidlive.chat.domain.ClientMessageId;
import com.ssafy.bidlive.chat.domain.MaskingCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** CHAT-01 이력 REST 응답·쿼리 파라미터·오류 상태 매핑을 검증한다. */
class ChatHistoryControllerTest {

	private static final String MESSAGE_ID =
		"01J8W3P2N9KQ4T123456789ABC";
	private static final String CLIENT_MESSAGE_ID =
		"018f6f4e-2a61-7c00-a1b2-334455667788";

	private ChatHistoryQueryService queryService;
	private MockMvc mockMvc;
	private UsernamePasswordAuthenticationToken authentication;

	@BeforeEach
	void setUp() {
		queryService = org.mockito.Mockito.mock(ChatHistoryQueryService.class);
		mockMvc = MockMvcBuilders.standaloneSetup(
			new ChatHistoryController(queryService)
		)
			.setControllerAdvice(new ChatHistoryExceptionHandler())
			.setMessageConverters(new MappingJackson2HttpMessageConverter(
				Jackson2ObjectMapperBuilder.json()
					.modules(new JavaTimeModule())
					.featuresToDisable(
						SerializationFeature.WRITE_DATES_AS_TIMESTAMPS
					)
					.build()
			))
			.build();
		authentication = UsernamePasswordAuthenticationToken.authenticated(
			"7",
			"token",
			List.of()
		);
	}

	@Test
	void returnsMessagesWithWebSocketCompatibleFields() throws Exception {
		when(queryService.getHistory(42L, 7L, MESSAGE_ID, 20))
			.thenReturn(new ChatHistoryPage(List.of(message()), true));

		mockMvc.perform(get("/api/v1/auctions/42/chats")
				.principal(authentication)
				.param("before", MESSAGE_ID)
				.param("size", "20"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.messages[0].messageId").value(MESSAGE_ID))
			.andExpect(jsonPath("$.messages[0].clientMessageId")
				.value(CLIENT_MESSAGE_ID))
			.andExpect(jsonPath("$.messages[0].memberId").value(7))
			.andExpect(jsonPath("$.messages[0].nickname").value("참여자"))
			.andExpect(jsonPath("$.messages[0].content").value("연락처 ******"))
			.andExpect(jsonPath("$.messages[0].isQuestion").value(true))
			.andExpect(jsonPath("$.messages[0].maskedFields[0]")
				.value("CONTACT"))
			.andExpect(jsonPath("$.messages[0].createdAt")
				.value("2026-08-01T00:00:00Z"))
			.andExpect(jsonPath("$.hasMore").value(true));

		verify(queryService).getHistory(42L, 7L, MESSAGE_ID, 20);
	}

	@Test
	void passesOmittedCursorAndSizeAsNull() throws Exception {
		when(queryService.getHistory(42L, 7L, null, null))
			.thenReturn(new ChatHistoryPage(List.of(), false));

		mockMvc.perform(get("/api/v1/auctions/42/chats")
				.principal(authentication))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.messages").isEmpty())
			.andExpect(jsonPath("$.hasMore").value(false));
	}

	@Test
	void rejectsMissingAuthenticationAndMalformedParameters() throws Exception {
		mockMvc.perform(get("/api/v1/auctions/42/chats"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

		mockMvc.perform(get("/api/v1/auctions/not-number/chats")
				.principal(authentication))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_AUCTION_ID"));

		mockMvc.perform(get("/api/v1/auctions/42/chats")
				.principal(authentication)
				.param("size", "many"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_PAGE_SIZE"));
	}

	@Test
	void mapsApplicationFailuresToStableHttpErrors() throws Exception {
		when(queryService.getHistory(42L, 7L, "invalid", null))
			.thenThrow(new ChatHistoryException(
				ChatHistoryErrorCode.INVALID_CURSOR
			));

		mockMvc.perform(get("/api/v1/auctions/42/chats")
				.principal(authentication)
				.param("before", "invalid"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_CURSOR"));

		when(queryService.getHistory(42L, 7L, null, null))
			.thenThrow(new ChatHistoryException(
				ChatHistoryErrorCode.ROOM_ACCESS_DENIED
			));

		mockMvc.perform(get("/api/v1/auctions/42/chats")
				.principal(authentication))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("ROOM_ACCESS_DENIED"));
	}

	private static ChatMessage message() {
		return new ChatMessage(
			new ChatMessageId(MESSAGE_ID),
			new ClientMessageId(CLIENT_MESSAGE_ID),
			42L,
			7L,
			"참여자",
			new ChatMessageContent("연락처 ******"),
			true,
			Set.of(MaskingCategory.CONTACT),
			Instant.parse("2026-08-01T00:00:00Z")
		);
	}
}
