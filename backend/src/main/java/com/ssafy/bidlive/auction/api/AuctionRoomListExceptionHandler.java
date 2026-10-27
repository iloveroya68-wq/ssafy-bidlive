package com.ssafy.bidlive.auction.api;

import com.ssafy.bidlive.auction.api.dto.AuctionRoomListErrorResponse;
import com.ssafy.bidlive.auction.application.AuctionRoomListException;
import com.ssafy.bidlive.auction.domain.AuctionRoomListErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 목록 조회 조건 오류를 명세의 오류 코드와 상태로 변환한다. */
@RestControllerAdvice(assignableTypes = AuctionRoomListController.class)
public class AuctionRoomListExceptionHandler {

	@ExceptionHandler(AuctionRoomListException.class)
	ResponseEntity<AuctionRoomListErrorResponse> handleRoomList(
		AuctionRoomListException exception
	) {
		AuctionRoomListErrorCode errorCode = exception.getErrorCode();
		return ResponseEntity.status(statusOf(errorCode))
			.body(new AuctionRoomListErrorResponse(errorCode.name(), messageOf(errorCode)));
	}

	private static HttpStatus statusOf(AuctionRoomListErrorCode errorCode) {
		return switch (errorCode) {
			case INVALID_AUCTION_STATUS, INVALID_CURSOR -> HttpStatus.BAD_REQUEST;
			case CATEGORY_NOT_FOUND -> HttpStatus.NOT_FOUND;
		};
	}

	private static String messageOf(AuctionRoomListErrorCode errorCode) {
		return switch (errorCode) {
			case INVALID_AUCTION_STATUS -> "지원하지 않는 경매 상태입니다.";
			case INVALID_CURSOR -> "커서 형식이 올바르지 않습니다.";
			case CATEGORY_NOT_FOUND -> "카테고리를 찾을 수 없습니다.";
		};
	}
}
