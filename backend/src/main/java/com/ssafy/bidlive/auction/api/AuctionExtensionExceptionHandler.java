package com.ssafy.bidlive.auction.api;

import com.ssafy.bidlive.auction.api.dto.AuctionRoomErrorResponse;
import com.ssafy.bidlive.auction.application.AuctionExtensionException;
import com.ssafy.bidlive.auction.domain.AuctionExtensionErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 방송 연장 실패를 BID-03 계약의 오류 코드와 HTTP 상태로 변환한다. */
@RestControllerAdvice(assignableTypes = AuctionExtensionController.class)
public class AuctionExtensionExceptionHandler {

	@ExceptionHandler(AuctionExtensionException.class)
	ResponseEntity<AuctionRoomErrorResponse> handleExtension(
		AuctionExtensionException exception
	) {
		AuctionExtensionErrorCode errorCode = exception.getReason();
		return ResponseEntity.status(statusOf(errorCode))
			.body(new AuctionRoomErrorResponse(errorCode.name(), messageOf(errorCode)));
	}

	private static HttpStatus statusOf(AuctionExtensionErrorCode errorCode) {
		return switch (errorCode) {
			case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
			case NOT_AUCTION_SELLER -> HttpStatus.FORBIDDEN;
			case AUCTION_NOT_FOUND -> HttpStatus.NOT_FOUND;
			case AUCTION_NOT_LIVE, EXTENSION_LIMIT_EXCEEDED, EXTENSION_TOO_LATE ->
				HttpStatus.CONFLICT;
		};
	}

	private static String messageOf(AuctionExtensionErrorCode errorCode) {
		return switch (errorCode) {
			case UNAUTHORIZED -> "로그인이 필요합니다.";
			case NOT_AUCTION_SELLER -> "이 경매의 판매자만 방송을 연장할 수 있습니다.";
			case AUCTION_NOT_FOUND -> "경매를 찾을 수 없습니다.";
			case AUCTION_NOT_LIVE -> "진행 중인 경매만 연장할 수 있습니다.";
			case EXTENSION_LIMIT_EXCEEDED -> "최대 연장 횟수를 초과했습니다.";
			case EXTENSION_TOO_LATE -> "마감 1분 미만인 경매는 연장할 수 없습니다.";
		};
	}
}
