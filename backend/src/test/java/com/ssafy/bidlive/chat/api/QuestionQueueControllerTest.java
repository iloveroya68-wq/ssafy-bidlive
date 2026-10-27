package com.ssafy.bidlive.chat.api;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.ssafy.bidlive.chat.application.QuestionQueueCommandService;
import com.ssafy.bidlive.chat.application.QuestionQueueException;
import com.ssafy.bidlive.chat.application.QuestionQueuePage;
import com.ssafy.bidlive.chat.application.QuestionQueueQueryService;
import com.ssafy.bidlive.chat.domain.ChatMessageId;
import com.ssafy.bidlive.chat.domain.QuestionQueueErrorCode;
import com.ssafy.bidlive.chat.domain.QuestionQueueItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** CHAT-06 질문 큐 REST 응답·인증·식별자·오류 상태 계약을 검증한다. */
class QuestionQueueControllerTest {

	private static final String QUESTION_ID =
		"01J8W3P2N9KQ4T123456789ABC";
	private static final Instant CREATED_AT =
		Instant.parse("2026-07-31T04:00:00Z");

	private QuestionQueueQueryService queryService;
	private QuestionQueueCommandService commandService;
	private MockMvc mockMvc;
	private UsernamePasswordAuthenticationToken authentication;

	@BeforeEach
	void setUp() {
		queryService = mock(QuestionQueueQueryService.class);
		commandService = mock(QuestionQueueCommandService.class);
		mockMvc = MockMvcBuilders.standaloneSetup(
			new QuestionQueueController(queryService, commandService)
		)
			.setControllerAdvice(new QuestionQueueExceptionHandler())
			.setMessageConverters(new MappingJackson2HttpMessageConverter(
				Jackson2ObjectMapperBuilder.json()
					.featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
					.build()))
			.build();
		authentication = UsernamePasswordAuthenticationToken.authenticated(
			"7",
			"token",
			List.of()
		);
	}

	@Test
	void returnsPendingQuestionPage() throws Exception {
		QuestionQueueItem item = new QuestionQueueItem(
			new ChatMessageId(QUESTION_ID),
			25L,
			"질문자",
			"흠집을 보여주세요",
			CREATED_AT
		);
		when(queryService.getPendingQuestions(
			42L,
			7L,
			QUESTION_ID,
			20
		)).thenReturn(new QuestionQueuePage(
			List.of(item),
			QUESTION_ID,
			false
		));

		mockMvc.perform(get("/api/v1/auctions/42/questions")
				.principal(authentication)
				.param("cursor", QUESTION_ID)
				.param("size", "20"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(1))
			.andExpect(jsonPath("$.items[0].questionId").value(QUESTION_ID))
			.andExpect(jsonPath("$.items[0].memberId").value(25))
			.andExpect(jsonPath("$.items[0].nickname").value("질문자"))
			.andExpect(jsonPath("$.items[0].content").value("흠집을 보여주세요"))
			.andExpect(jsonPath("$.items[0].createdAt")
				.value("2026-07-31T04:00:00Z"))
			.andExpect(jsonPath("$.nextCursor").value(QUESTION_ID))
			.andExpect(jsonPath("$.hasNext").value(false));

		verify(queryService).getPendingQuestions(
			42L,
			7L,
			QUESTION_ID,
			20
		);
	}

	@Test
	void resolvesQuestionWithNoContent() throws Exception {
		mockMvc.perform(delete("/api/v1/auctions/42/questions/{questionId}", QUESTION_ID)
				.principal(authentication))
			.andExpect(status().isNoContent());

		verify(commandService).resolve(
			42L,
			7L,
			new ChatMessageId(QUESTION_ID)
		);
	}

	@Test
	void rejectsMissingAuthentication() throws Exception {
		mockMvc.perform(get("/api/v1/auctions/42/questions"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	@Test
	void rejectsInvalidAuctionAndQuestionIdentifiers() throws Exception {
		mockMvc.perform(get("/api/v1/auctions/not-number/questions")
				.principal(authentication))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_AUCTION_ID"));

		mockMvc.perform(delete("/api/v1/auctions/42/questions/not-ulid")
				.principal(authentication))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("QUESTION_NOT_FOUND"));
	}

	@Test
	void rejectsNonNumericPageSizeWithStableCode() throws Exception {
		mockMvc.perform(get("/api/v1/auctions/42/questions")
				.principal(authentication)
				.param("size", "large"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_PAGE_SIZE"));
	}

	@Test
	void mapsApplicationFailureToStableError() throws Exception {
		doThrow(new QuestionQueueException(
			QuestionQueueErrorCode.NOT_AUCTION_HOST
		)).when(commandService).resolve(
			42L,
			7L,
			new ChatMessageId(QUESTION_ID)
		);

		mockMvc.perform(delete("/api/v1/auctions/42/questions/{questionId}", QUESTION_ID)
				.principal(authentication))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("NOT_AUCTION_HOST"));
	}
}
