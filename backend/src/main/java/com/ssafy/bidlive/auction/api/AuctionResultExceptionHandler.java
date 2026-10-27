package com.ssafy.bidlive.auction.api;

import java.time.Instant;

import com.ssafy.bidlive.auction.api.dto.AuctionResultErrorResponse;
import com.ssafy.bidlive.auction.application.AuctionResultQueryException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 경매 결과 조회 예외를 명세의 HTTP 상태와 오류 응답으로 변환합니다. */
@RestControllerAdvice(assignableTypes = AuctionResultController.class)
public class AuctionResultExceptionHandler {

	@ExceptionHandler(AuctionResultQueryException.class)
	public ResponseEntity<AuctionResultErrorResponse> handle(AuctionResultQueryException exception) {
		HttpStatus status = switch (exception.getErrorCode()) {
			case INVALID_AUCTION_ID -> HttpStatus.BAD_REQUEST;
			case AUCTION_NOT_FOUND -> HttpStatus.NOT_FOUND;
			case PREDICTION_RESULT_NOT_AVAILABLE -> HttpStatus.CONFLICT;
		};
		String message = switch (exception.getErrorCode()) {
			case INVALID_AUCTION_ID -> "경매 ID 형식이 올바르지 않습니다.";
			case AUCTION_NOT_FOUND -> "경매방을 찾을 수 없습니다.";
			case PREDICTION_RESULT_NOT_AVAILABLE -> "아직 결과가 확정되지 않았습니다.";
		};
		return ResponseEntity.status(status)
			.body(new AuctionResultErrorResponse(
				Instant.now(),
				status.value(),
				exception.getErrorCode().name(),
				message
			));
	}
}
