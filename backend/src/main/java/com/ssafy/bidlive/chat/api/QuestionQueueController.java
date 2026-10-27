package com.ssafy.bidlive.chat.api;

import static com.ssafy.bidlive.chat.domain.QuestionQueueErrorCode.INVALID_AUCTION_ID;
import static com.ssafy.bidlive.chat.domain.QuestionQueueErrorCode.QUESTION_NOT_FOUND;
import static com.ssafy.bidlive.chat.domain.QuestionQueueErrorCode.UNAUTHORIZED;

import com.ssafy.bidlive.chat.api.dto.QuestionQueueResponse;
import com.ssafy.bidlive.chat.application.QuestionQueueCommandService;
import com.ssafy.bidlive.chat.application.QuestionQueueException;
import com.ssafy.bidlive.chat.application.QuestionQueueQueryService;
import com.ssafy.bidlive.chat.domain.ChatMessageId;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 판매자의 PENDING 질문 복원과 답변 완료 처리를 HTTP API로 제공한다. */
@RestController
@RequestMapping("/api/v1/auctions/{auctionId}/questions")
public class QuestionQueueController {

	private final QuestionQueueQueryService queryService;
	private final QuestionQueueCommandService commandService;

	public QuestionQueueController(
		QuestionQueueQueryService queryService,
		QuestionQueueCommandService commandService
	) {
		this.queryService = queryService;
		this.commandService = commandService;
	}

	@GetMapping
	public QuestionQueueResponse getPendingQuestions(
		@PathVariable String auctionId,
		@RequestParam(required = false) String cursor,
		@RequestParam(required = false) Integer size,
		Authentication authentication
	) {
		return QuestionQueueResponse.from(queryService.getPendingQuestions(
			parseAuctionId(auctionId),
			authenticatedMemberId(authentication),
			cursor,
			size
		));
	}

	@DeleteMapping("/{questionId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void resolve(
		@PathVariable String auctionId,
		@PathVariable String questionId,
		Authentication authentication
	) {
		commandService.resolve(
			parseAuctionId(auctionId),
			authenticatedMemberId(authentication),
			parseQuestionId(questionId)
		);
	}

	private static long parseAuctionId(String value) {
		try {
			long parsed = Long.parseLong(value);
			if (parsed <= 0) {
				throw new NumberFormatException("id must be positive");
			}
			return parsed;
		} catch (NumberFormatException exception) {
			throw new QuestionQueueException(INVALID_AUCTION_ID, exception);
		}
	}

	private static ChatMessageId parseQuestionId(String value) {
		try {
			return new ChatMessageId(value);
		} catch (IllegalArgumentException exception) {
			throw new QuestionQueueException(QUESTION_NOT_FOUND, exception);
		}
	}

	private static Long authenticatedMemberId(Authentication authentication) {
		if (authentication == null || !authentication.isAuthenticated()) {
			throw new QuestionQueueException(UNAUTHORIZED);
		}
		try {
			long memberId = Long.parseLong(authentication.getName());
			if (memberId <= 0) {
				throw new NumberFormatException("memberId must be positive");
			}
			return memberId;
		} catch (IllegalArgumentException exception) {
			throw new QuestionQueueException(UNAUTHORIZED, exception);
		}
	}
}
