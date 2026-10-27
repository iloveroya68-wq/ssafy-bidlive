package com.ssafy.bidlive.auction.api;

import com.ssafy.bidlive.auction.api.dto.AuctionRoomErrorResponse;
import com.ssafy.bidlive.auction.application.AuctionRoomDeleteException;
import com.ssafy.bidlive.auction.domain.AuctionRoomDeleteErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 경매방 삭제 실패를 명세의 오류 코드와 상태로 변환한다. */
@RestControllerAdvice(assignableTypes = AuctionRoomDeleteController.class)
public class AuctionRoomDeleteExceptionHandler {

	@ExceptionHandler(AuctionRoomDeleteException.class)
	ResponseEntity<AuctionRoomErrorResponse> handleRoomDelete(
		AuctionRoomDeleteException exception
	) {
		AuctionRoomDeleteErrorCode errorCode = exception.getErrorCode();
		return ResponseEntity.status(statusOf(errorCode))
			.body(new AuctionRoomErrorResponse(errorCode.name(), messageOf(errorCode)));
	}

	private static HttpStatus statusOf(AuctionRoomDeleteErrorCode errorCode) {
		return switch (errorCode) {
			case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
			case NOT_AUCTION_SELLER -> HttpStatus.FORBIDDEN;
			case AUCTION_NOT_FOUND -> HttpStatus.NOT_FOUND;
			case AUCTION_ALREADY_STARTED, AUCTION_HAS_PARTICIPATION -> HttpStatus.CONFLICT;
		};
	}

	private static String messageOf(AuctionRoomDeleteErrorCode errorCode) {
		return switch (errorCode) {
			case UNAUTHORIZED -> "로그인이 필요합니다.";
			case NOT_AUCTION_SELLER -> "이 경매방의 판매자만 삭제할 수 있습니다.";
			case AUCTION_NOT_FOUND -> "경매방을 찾을 수 없습니다.";
			case AUCTION_ALREADY_STARTED -> "이미 시작된 경매방은 삭제할 수 없습니다.";
			case AUCTION_HAS_PARTICIPATION -> "참여 이력이 있는 경매방은 삭제할 수 없습니다.";
		};
	}
}
