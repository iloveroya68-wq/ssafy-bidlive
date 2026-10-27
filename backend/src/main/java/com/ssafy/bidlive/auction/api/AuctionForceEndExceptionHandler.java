package com.ssafy.bidlive.auction.api;

import com.ssafy.bidlive.auction.api.dto.AuctionForceEndErrorResponse;
import com.ssafy.bidlive.auction.application.AuctionForceEndException;
import com.ssafy.bidlive.auction.domain.AuctionForceEndErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 강제 종료 실패를 HTTP 상태와 오류 응답으로 변환한다. */
@RestControllerAdvice(assignableTypes = AdminAuctionForceEndController.class)
public class AuctionForceEndExceptionHandler {

	@ExceptionHandler(AuctionForceEndException.class)
	ResponseEntity<AuctionForceEndErrorResponse> handleForceEnd(
		AuctionForceEndException exception
	) {
		AuctionForceEndErrorCode errorCode = exception.getErrorCode();
		return ResponseEntity.status(httpStatus(errorCode))
			.body(new AuctionForceEndErrorResponse(
				errorCode.name(),
				message(errorCode)
			));
	}

	private HttpStatus httpStatus(AuctionForceEndErrorCode errorCode) {
		return switch (errorCode) {
			case INVALID_AUCTION_ID, INVALID_REASON -> HttpStatus.BAD_REQUEST;
			case AUCTION_NOT_FOUND -> HttpStatus.NOT_FOUND;
			case AUCTION_NOT_ENDABLE -> HttpStatus.CONFLICT;
		};
	}

	private String message(AuctionForceEndErrorCode errorCode) {
		return switch (errorCode) {
			case INVALID_AUCTION_ID -> "경매 ID가 올바르지 않습니다.";
			case INVALID_REASON -> "종료 사유가 필요합니다.";
			case AUCTION_NOT_FOUND -> "경매가 존재하지 않습니다.";
			case AUCTION_NOT_ENDABLE ->
				"대기 중이거나 진행 중인 경매만 강제 종료할 수 있습니다.";
		};
	}
}
