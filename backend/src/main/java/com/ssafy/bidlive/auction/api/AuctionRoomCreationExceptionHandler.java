package com.ssafy.bidlive.auction.api;

import static com.ssafy.bidlive.auction.domain.AuctionRoomCreationErrorCode.INVALID_PRODUCT_REQUEST;

import com.ssafy.bidlive.auction.api.dto.AuctionRoomCreationErrorResponse;
import com.ssafy.bidlive.auction.application.AuctionRoomCreationException;
import com.ssafy.bidlive.auction.domain.AuctionRoomCreationErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 경매방 생성 실패를 명세의 HTTP 상태와 서비스 오류 코드로 변환합니다. */
@RestControllerAdvice(assignableTypes = AuctionRoomCreationController.class)
public class AuctionRoomCreationExceptionHandler {

	@ExceptionHandler(AuctionRoomCreationException.class)
	ResponseEntity<AuctionRoomCreationErrorResponse> handleCreation(
		AuctionRoomCreationException exception
	) {
		AuctionRoomCreationErrorCode errorCode = exception.getErrorCode();
		return ResponseEntity.status(status(errorCode))
			.body(new AuctionRoomCreationErrorResponse(
				errorCode.name(),
				message(errorCode)
			));
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	ResponseEntity<AuctionRoomCreationErrorResponse> handleUnreadableRequest(
		HttpMessageNotReadableException exception
	) {
		return ResponseEntity.badRequest()
			.body(new AuctionRoomCreationErrorResponse(
				INVALID_PRODUCT_REQUEST.name(),
				message(INVALID_PRODUCT_REQUEST)
			));
	}

	private HttpStatus status(AuctionRoomCreationErrorCode errorCode) {
		return switch (errorCode) {
			case INVALID_PRODUCT_REQUEST, INVALID_AUCTION_REQUEST ->
				HttpStatus.BAD_REQUEST;
			case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
			case INVALID_SCHEDULE_TIME -> HttpStatus.UNPROCESSABLE_ENTITY;
		};
	}

	private String message(AuctionRoomCreationErrorCode errorCode) {
		return switch (errorCode) {
			case INVALID_PRODUCT_REQUEST -> "상품 정보가 올바르지 않습니다.";
			case INVALID_AUCTION_REQUEST -> "경매 조건이 올바르지 않습니다.";
			case UNAUTHORIZED -> "인증된 판매자만 경매방을 생성할 수 있습니다.";
			case INVALID_SCHEDULE_TIME -> "예약 시각이 유효하지 않습니다.";
		};
	}
}
