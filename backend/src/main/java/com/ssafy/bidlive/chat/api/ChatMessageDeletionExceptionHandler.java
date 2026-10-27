package com.ssafy.bidlive.chat.api;

import com.ssafy.bidlive.chat.api.dto.ChatMessageDeletionErrorResponse;
import com.ssafy.bidlive.chat.application.ChatMessageDeletionErrorCode;
import com.ssafy.bidlive.chat.application.ChatMessageDeletionException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 채팅 삭제 실패를 공개 가능한 안정적 HTTP 오류 응답으로 변환합니다. */
@RestControllerAdvice(assignableTypes = ChatMessageDeletionController.class)
public class ChatMessageDeletionExceptionHandler {

	@ExceptionHandler(ChatMessageDeletionException.class)
	ResponseEntity<ChatMessageDeletionErrorResponse> handle(
		ChatMessageDeletionException exception
	) {
		ChatMessageDeletionErrorCode code = exception.errorCode();
		return ResponseEntity.status(statusOf(code)).body(
			new ChatMessageDeletionErrorResponse(
				code.name(),
				code.publicMessage()
			)
		);
	}

	private static HttpStatus statusOf(ChatMessageDeletionErrorCode code) {
		return switch (code) {
			case INVALID_AUCTION_ID, INVALID_MESSAGE_ID ->
				HttpStatus.BAD_REQUEST;
			case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
			case CHAT_DELETE_FORBIDDEN -> HttpStatus.FORBIDDEN;
			case MESSAGE_NOT_FOUND -> HttpStatus.NOT_FOUND;
		};
	}
}
