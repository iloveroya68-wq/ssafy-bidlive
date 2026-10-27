package com.ssafy.bidlive.auction.api;

import com.ssafy.bidlive.auction.api.dto.AuctionForceEndErrorResponse;
import com.ssafy.bidlive.auction.application.AuctionSellerEndException;
import com.ssafy.bidlive.auction.domain.AuctionSellerEndErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** ROOM-09 판매자 종료 실패를 명세의 오류 코드·상태로 옮긴다. */
@RestControllerAdvice(assignableTypes = AuctionSellerEndController.class)
public class AuctionSellerEndExceptionHandler {

	@ExceptionHandler(AuctionSellerEndException.class)
	ResponseEntity<AuctionForceEndErrorResponse> handleSellerEnd(
		AuctionSellerEndException exception
	) {
		AuctionSellerEndErrorCode errorCode = exception.getErrorCode();
		return ResponseEntity.status(httpStatus(errorCode))
			.body(new AuctionForceEndErrorResponse(
				errorCode.name(),
				message(errorCode)
			));
	}

	private HttpStatus httpStatus(AuctionSellerEndErrorCode errorCode) {
		return switch (errorCode) {
			case INVALID_AUCTION_ID -> HttpStatus.BAD_REQUEST;
			case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
			case AUCTION_NOT_FOUND -> HttpStatus.NOT_FOUND;
			case NOT_AUCTION_SELLER -> HttpStatus.FORBIDDEN;
			case AUCTION_NOT_ENDABLE -> HttpStatus.CONFLICT;
		};
	}

	private String message(AuctionSellerEndErrorCode errorCode) {
		return switch (errorCode) {
			case INVALID_AUCTION_ID -> "경매 ID가 올바르지 않습니다.";
			case UNAUTHORIZED -> "로그인이 필요합니다.";
			case AUCTION_NOT_FOUND -> "경매가 존재하지 않습니다.";
			case NOT_AUCTION_SELLER -> "이 경매의 판매자만 종료할 수 있습니다.";
			case AUCTION_NOT_ENDABLE -> "이미 종료된 경매입니다.";
		};
	}
}
