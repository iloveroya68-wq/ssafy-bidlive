package com.ssafy.bidlive.member.api;

import com.ssafy.bidlive.member.api.dto.WonAuctionQueryErrorResponse;
import com.ssafy.bidlive.member.application.WonAuctionQueryException;
import com.ssafy.bidlive.member.domain.WonAuctionQueryErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 낙찰 받은 경매 조회 실패를 명세의 HTTP 상태와 오류 응답으로 변환한다. */
@RestControllerAdvice(assignableTypes = WonAuctionQueryController.class)
public class WonAuctionQueryExceptionHandler {

	@ExceptionHandler(WonAuctionQueryException.class)
	ResponseEntity<WonAuctionQueryErrorResponse> handle(
		WonAuctionQueryException exception
	) {
		WonAuctionQueryErrorCode errorCode = exception.getErrorCode();
		return ResponseEntity.status(httpStatus(errorCode))
			.body(new WonAuctionQueryErrorResponse(
				errorCode.name(),
				message(errorCode)
			));
	}

	private HttpStatus httpStatus(WonAuctionQueryErrorCode errorCode) {
		return switch (errorCode) {
			case INVALID_CURSOR -> HttpStatus.BAD_REQUEST;
			case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
		};
	}

	private String message(WonAuctionQueryErrorCode errorCode) {
		return switch (errorCode) {
			case INVALID_CURSOR -> "커서 형식이 올바르지 않습니다.";
			case UNAUTHORIZED -> "인증이 필요합니다.";
		};
	}
}
