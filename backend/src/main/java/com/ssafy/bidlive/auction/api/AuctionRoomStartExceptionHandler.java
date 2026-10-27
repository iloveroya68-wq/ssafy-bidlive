package com.ssafy.bidlive.auction.api;

import com.ssafy.bidlive.auction.api.dto.AuctionRoomErrorResponse;
import com.ssafy.bidlive.auction.application.AuctionRoomStartException;
import com.ssafy.bidlive.auction.domain.AuctionRoomStartErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 방송 시작 실패를 명세의 오류 코드와 상태로 변환한다. */
@RestControllerAdvice(assignableTypes = AuctionRoomStartController.class)
public class AuctionRoomStartExceptionHandler {

	@ExceptionHandler(AuctionRoomStartException.class)
	ResponseEntity<AuctionRoomErrorResponse> handleRoomStart(
		AuctionRoomStartException exception
	) {
		AuctionRoomStartErrorCode errorCode = exception.getErrorCode();
		return ResponseEntity.status(statusOf(errorCode))
			.body(new AuctionRoomErrorResponse(errorCode.name(), messageOf(errorCode)));
	}

	private static HttpStatus statusOf(AuctionRoomStartErrorCode errorCode) {
		return switch (errorCode) {
			case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
			case NOT_AUCTION_SELLER -> HttpStatus.FORBIDDEN;
			case AUCTION_NOT_FOUND -> HttpStatus.NOT_FOUND;
			case AUCTION_START_NOT_ALLOWED, AUCTION_ALREADY_STARTED -> HttpStatus.CONFLICT;
			case AUCTION_MODERATION_BLOCKED -> HttpStatus.UNPROCESSABLE_ENTITY;
		};
	}

	private static String messageOf(AuctionRoomStartErrorCode errorCode) {
		return switch (errorCode) {
			case UNAUTHORIZED -> "로그인이 필요합니다.";
			case NOT_AUCTION_SELLER -> "이 경매방의 판매자만 방송을 시작할 수 있습니다.";
			case AUCTION_NOT_FOUND -> "경매방을 찾을 수 없습니다.";
			case AUCTION_START_NOT_ALLOWED -> "현재 상태에서는 방송을 시작할 수 없습니다.";
			case AUCTION_ALREADY_STARTED -> "이미 시작된 경매방입니다.";
			case AUCTION_MODERATION_BLOCKED -> "검열 결과로 방송을 시작할 수 없습니다.";
		};
	}
}
