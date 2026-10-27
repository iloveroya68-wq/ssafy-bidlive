package com.ssafy.bidlive.auction.api;

import com.ssafy.bidlive.auction.api.dto.AuctionRoomErrorResponse;
import com.ssafy.bidlive.auction.application.AuctionRoomJoinException;
import com.ssafy.bidlive.auction.domain.AuctionRoomJoinErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 대기방 입장 실패를 명세의 오류 코드와 상태로 변환한다. */
@RestControllerAdvice(assignableTypes = AuctionRoomJoinController.class)
public class AuctionRoomJoinExceptionHandler {

	@ExceptionHandler(AuctionRoomJoinException.class)
	ResponseEntity<AuctionRoomErrorResponse> handleRoomJoin(AuctionRoomJoinException exception) {
		AuctionRoomJoinErrorCode errorCode = exception.getErrorCode();
		return ResponseEntity.status(statusOf(errorCode))
			.body(new AuctionRoomErrorResponse(errorCode.name(), messageOf(errorCode)));
	}

	private static HttpStatus statusOf(AuctionRoomJoinErrorCode errorCode) {
		return switch (errorCode) {
			case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
			case ROOM_ACCESS_DENIED -> HttpStatus.FORBIDDEN;
			case AUCTION_ROOM_NOT_FOUND -> HttpStatus.NOT_FOUND;
			case AUCTION_ALREADY_ENDED -> HttpStatus.CONFLICT;
		};
	}

	private static String messageOf(AuctionRoomJoinErrorCode errorCode) {
		return switch (errorCode) {
			case UNAUTHORIZED -> "로그인이 필요합니다.";
			case ROOM_ACCESS_DENIED -> "입장할 수 없는 계정입니다.";
			case AUCTION_ROOM_NOT_FOUND -> "경매방을 찾을 수 없습니다.";
			case AUCTION_ALREADY_ENDED -> "이미 종료된 경매방입니다.";
		};
	}
}
