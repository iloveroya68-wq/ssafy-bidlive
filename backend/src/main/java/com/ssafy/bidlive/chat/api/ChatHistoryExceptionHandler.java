package com.ssafy.bidlive.chat.api;

import com.ssafy.bidlive.chat.api.dto.ChatHistoryErrorResponse;
import com.ssafy.bidlive.chat.application.ChatHistoryErrorCode;
import com.ssafy.bidlive.chat.application.ChatHistoryException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** 채팅 이력 조회 실패를 HTTP 상태와 안정적인 오류 응답으로 변환한다. */
@RestControllerAdvice(assignableTypes = ChatHistoryController.class)
public class ChatHistoryExceptionHandler {

	@ExceptionHandler(ChatHistoryException.class)
	ResponseEntity<ChatHistoryErrorResponse> handle(
		ChatHistoryException exception
	) {
		ChatHistoryErrorCode code = exception.errorCode();
		return ResponseEntity.status(statusOf(code))
			.body(new ChatHistoryErrorResponse(code.name(), messageOf(code)));
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	ResponseEntity<ChatHistoryErrorResponse> handleTypeMismatch(
		MethodArgumentTypeMismatchException exception
	) {
		ChatHistoryErrorCode code = ChatHistoryErrorCode.INVALID_PAGE_SIZE;
		return ResponseEntity.badRequest()
			.body(new ChatHistoryErrorResponse(code.name(), messageOf(code)));
	}

	private static HttpStatus statusOf(ChatHistoryErrorCode code) {
		return switch (code) {
			case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
			case INVALID_AUCTION_ID, INVALID_CURSOR, INVALID_PAGE_SIZE ->
				HttpStatus.BAD_REQUEST;
			case ROOM_ACCESS_DENIED -> HttpStatus.FORBIDDEN;
			case AUCTION_NOT_FOUND -> HttpStatus.NOT_FOUND;
		};
	}

	private static String messageOf(ChatHistoryErrorCode code) {
		return switch (code) {
			case UNAUTHORIZED -> "인증이 필요합니다.";
			case INVALID_AUCTION_ID -> "경매 ID가 올바르지 않습니다.";
			case INVALID_CURSOR -> "채팅 이력 커서가 올바르지 않습니다.";
			case INVALID_PAGE_SIZE -> "페이지 크기는 1 이상 100 이하여야 합니다.";
			case AUCTION_NOT_FOUND -> "경매를 찾을 수 없습니다.";
			case ROOM_ACCESS_DENIED -> "채팅 이력을 조회할 권한이 없습니다.";
		};
	}
}
