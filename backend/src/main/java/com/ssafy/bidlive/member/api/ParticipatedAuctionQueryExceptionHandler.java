package com.ssafy.bidlive.member.api;

import com.ssafy.bidlive.member.api.dto.ParticipatedAuctionQueryErrorResponse;
import com.ssafy.bidlive.member.application.ParticipatedAuctionQueryException;
import com.ssafy.bidlive.member.domain.ParticipatedAuctionQueryErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 참여 경매 조회 실패를 명세의 HTTP 상태와 오류 응답으로 변환한다. */
@RestControllerAdvice(
	assignableTypes = ParticipatedAuctionQueryController.class
)
public class ParticipatedAuctionQueryExceptionHandler {

	@ExceptionHandler(ParticipatedAuctionQueryException.class)
	ResponseEntity<ParticipatedAuctionQueryErrorResponse> handle(
		ParticipatedAuctionQueryException exception
	) {
		ParticipatedAuctionQueryErrorCode errorCode =
			exception.getErrorCode();
		return ResponseEntity.status(httpStatus(errorCode))
			.body(new ParticipatedAuctionQueryErrorResponse(
				errorCode.name(),
				message(errorCode)
			));
	}

	private HttpStatus httpStatus(
		ParticipatedAuctionQueryErrorCode errorCode
	) {
		return switch (errorCode) {
			case INVALID_CURSOR -> HttpStatus.BAD_REQUEST;
			case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
		};
	}

	private String message(ParticipatedAuctionQueryErrorCode errorCode) {
		return switch (errorCode) {
			case INVALID_CURSOR -> "커서 형식이 올바르지 않습니다.";
			case UNAUTHORIZED -> "인증이 필요합니다.";
		};
	}
}
