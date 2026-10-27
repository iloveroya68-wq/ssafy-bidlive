package com.ssafy.bidlive.chat.api;

import com.ssafy.bidlive.chat.api.dto.QuestionQueueErrorResponse;
import com.ssafy.bidlive.chat.application.QuestionQueueException;
import com.ssafy.bidlive.chat.domain.QuestionQueueErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** 질문 큐 실패를 HTTP 상태와 안정적인 오류 응답으로 변환한다. */
@RestControllerAdvice(assignableTypes = QuestionQueueController.class)
public class QuestionQueueExceptionHandler {

	@ExceptionHandler(QuestionQueueException.class)
	ResponseEntity<QuestionQueueErrorResponse> handle(
		QuestionQueueException exception
	) {
		QuestionQueueErrorCode code = exception.errorCode();
		return ResponseEntity.status(statusOf(code))
			.body(new QuestionQueueErrorResponse(code.name(), messageOf(code)));
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	ResponseEntity<QuestionQueueErrorResponse> handleTypeMismatch(
		MethodArgumentTypeMismatchException exception
	) {
		QuestionQueueErrorCode code =
			QuestionQueueErrorCode.INVALID_PAGE_SIZE;
		return ResponseEntity.badRequest().body(
			new QuestionQueueErrorResponse(code.name(), messageOf(code))
		);
	}

	private static HttpStatus statusOf(QuestionQueueErrorCode code) {
		return switch (code) {
			case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
			case INVALID_AUCTION_ID, INVALID_CURSOR, INVALID_PAGE_SIZE ->
				HttpStatus.BAD_REQUEST;
			case NOT_AUCTION_HOST -> HttpStatus.FORBIDDEN;
			case QUESTION_NOT_FOUND -> HttpStatus.NOT_FOUND;
		};
	}

	private static String messageOf(QuestionQueueErrorCode code) {
		return switch (code) {
			case UNAUTHORIZED -> "인증이 필요합니다.";
			case INVALID_AUCTION_ID -> "경매 ID가 올바르지 않습니다.";
			case NOT_AUCTION_HOST -> "해당 경매의 판매자가 아닙니다.";
			case INVALID_CURSOR -> "질문 커서가 올바르지 않습니다.";
			case INVALID_PAGE_SIZE -> "페이지 크기는 1 이상 100 이하여야 합니다.";
			case QUESTION_NOT_FOUND -> "질문을 찾을 수 없습니다.";
		};
	}
}
