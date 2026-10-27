package com.ssafy.bidlive.chat.api;

import com.ssafy.bidlive.chat.api.dto.PinnedNoticeErrorResponse;
import com.ssafy.bidlive.chat.application.PinnedNoticeException;
import com.ssafy.bidlive.chat.domain.PinnedNoticeErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 고정 공지 실패를 HTTP 상태와 안정적인 오류 응답으로 변환합니다. */
@RestControllerAdvice(assignableTypes = PinnedNoticeController.class)
public class PinnedNoticeExceptionHandler {

	@ExceptionHandler(PinnedNoticeException.class)
	ResponseEntity<PinnedNoticeErrorResponse> handle(
		PinnedNoticeException exception
	) {
		PinnedNoticeErrorCode code = exception.errorCode();
		return ResponseEntity.status(statusOf(code))
			.body(new PinnedNoticeErrorResponse(code.name(), messageOf(code)));
	}

	@ExceptionHandler({
		MethodArgumentNotValidException.class,
		HttpMessageNotReadableException.class
	})
	ResponseEntity<PinnedNoticeErrorResponse> handleInvalidRequest(
		Exception exception
	) {
		return ResponseEntity.badRequest().body(new PinnedNoticeErrorResponse(
			PinnedNoticeErrorCode.INVALID_MESSAGE_ID.name(),
			messageOf(PinnedNoticeErrorCode.INVALID_MESSAGE_ID)
		));
	}

	private static HttpStatus statusOf(PinnedNoticeErrorCode code) {
		return switch (code) {
			case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
			case INVALID_AUCTION_ID, INVALID_MESSAGE_ID -> HttpStatus.BAD_REQUEST;
			case NOT_AUCTION_HOST -> HttpStatus.FORBIDDEN;
			case AUCTION_NOT_FOUND, MESSAGE_NOT_FOUND -> HttpStatus.NOT_FOUND;
		};
	}

	private static String messageOf(PinnedNoticeErrorCode code) {
		return switch (code) {
			case UNAUTHORIZED -> "인증이 필요합니다.";
			case INVALID_AUCTION_ID -> "경매 ID가 올바르지 않습니다.";
			case INVALID_MESSAGE_ID -> "메시지 ID가 올바르지 않습니다.";
			case AUCTION_NOT_FOUND -> "경매를 찾을 수 없습니다.";
			case NOT_AUCTION_HOST -> "해당 경매의 판매자가 아닙니다.";
			case MESSAGE_NOT_FOUND -> "고정할 메시지를 찾을 수 없습니다.";
		};
	}
}
