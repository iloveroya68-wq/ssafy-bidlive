package com.ssafy.bidlive.auction.api;

import com.ssafy.bidlive.auction.api.dto.AuctionRoomErrorResponse;
import com.ssafy.bidlive.auction.application.AuctionRoomUpdateException;
import com.ssafy.bidlive.auction.domain.AuctionRoomUpdateErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 경매방 수정 실패를 명세의 오류 코드와 상태로 변환한다. */
@RestControllerAdvice(assignableTypes = AuctionRoomUpdateController.class)
public class AuctionRoomUpdateExceptionHandler {

	@ExceptionHandler(AuctionRoomUpdateException.class)
	ResponseEntity<AuctionRoomErrorResponse> handleRoomUpdate(
		AuctionRoomUpdateException exception
	) {
		AuctionRoomUpdateErrorCode errorCode = exception.getErrorCode();
		return ResponseEntity.status(statusOf(errorCode))
			.body(new AuctionRoomErrorResponse(errorCode.name(), messageOf(errorCode)));
	}

	/**
	 * 깨진 JSON·해석 불가한 시각 문자열도 요청 값 오류다. 그대로 두면 500이 나가는데,
	 * 판매자가 고칠 수 있는 문제라 400이 맞다.
	 */
	@ExceptionHandler(HttpMessageNotReadableException.class)
	ResponseEntity<AuctionRoomErrorResponse> handleUnreadableBody(
		HttpMessageNotReadableException exception
	) {
		return ResponseEntity.badRequest()
			.body(new AuctionRoomErrorResponse(
				AuctionRoomUpdateErrorCode.INVALID_AUCTION_REQUEST.name(),
				messageOf(AuctionRoomUpdateErrorCode.INVALID_AUCTION_REQUEST)
			));
	}

	private static HttpStatus statusOf(AuctionRoomUpdateErrorCode errorCode) {
		return switch (errorCode) {
			case EMPTY_UPDATE_REQUEST, INVALID_AUCTION_REQUEST -> HttpStatus.BAD_REQUEST;
			case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
			case NOT_AUCTION_SELLER -> HttpStatus.FORBIDDEN;
			case AUCTION_NOT_FOUND -> HttpStatus.NOT_FOUND;
			case AUCTION_UPDATE_NOT_ALLOWED -> HttpStatus.CONFLICT;
		};
	}

	private static String messageOf(AuctionRoomUpdateErrorCode errorCode) {
		return switch (errorCode) {
			case EMPTY_UPDATE_REQUEST -> "수정할 값이 없습니다.";
			case INVALID_AUCTION_REQUEST -> "수정 값이 올바르지 않습니다.";
			case UNAUTHORIZED -> "로그인이 필요합니다.";
			case NOT_AUCTION_SELLER -> "이 경매방의 판매자만 수정할 수 있습니다.";
			case AUCTION_NOT_FOUND -> "경매방을 찾을 수 없습니다.";
			case AUCTION_UPDATE_NOT_ALLOWED -> "이미 시작된 경매방은 수정할 수 없습니다.";
		};
	}
}
