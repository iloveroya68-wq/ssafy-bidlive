package com.ssafy.bidlive.auction.api;

import java.time.Instant;

import com.ssafy.bidlive.auction.api.dto.AuctionRoomDetailErrorResponse;
import com.ssafy.bidlive.auction.application.AuctionRoomDetailException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 경매방 상세 조회 예외를 명세의 HTTP 상태와 오류 응답으로 변환합니다. */
@RestControllerAdvice(assignableTypes = AuctionRoomDetailController.class)
public class AuctionRoomDetailExceptionHandler {

	@ExceptionHandler(AuctionRoomDetailException.class)
	public ResponseEntity<AuctionRoomDetailErrorResponse> handle(
		AuctionRoomDetailException exception
	) {
		HttpStatus status = switch (exception.getErrorCode()) {
			case INVALID_AUCTION_ID -> HttpStatus.BAD_REQUEST;
			case AUCTION_NOT_FOUND -> HttpStatus.NOT_FOUND;
			case AUCTION_DELETED -> HttpStatus.GONE;
		};
		String message = switch (exception.getErrorCode()) {
			case INVALID_AUCTION_ID -> "경매 ID 형식이 올바르지 않습니다.";
			case AUCTION_NOT_FOUND -> "경매방을 찾을 수 없습니다.";
			case AUCTION_DELETED -> "삭제된 경매방입니다.";
		};
		return ResponseEntity.status(status)
			.body(new AuctionRoomDetailErrorResponse(
				Instant.now(),
				status.value(),
				exception.getErrorCode().name(),
				message
			));
	}
}
