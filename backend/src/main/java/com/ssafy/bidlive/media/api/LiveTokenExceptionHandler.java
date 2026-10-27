package com.ssafy.bidlive.media.api;

import com.ssafy.bidlive.media.api.dto.LiveErrorResponse;
import com.ssafy.bidlive.media.application.LiveTokenRejectedException;
import com.ssafy.bidlive.media.domain.LiveTokenFailureCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** LIVE-01의 인증·방 접근 권한 실패를 명세상 HTTP 오류로 변환합니다. */
@RestControllerAdvice(assignableTypes = LiveTokenController.class)
public class LiveTokenExceptionHandler {

	@ExceptionHandler(LiveTokenRejectedException.class)
	ResponseEntity<LiveErrorResponse> handleRejectedToken(LiveTokenRejectedException exception) {
		LiveTokenFailureCode failureCode = exception.getFailureCode();
		return ResponseEntity.status(httpStatus(failureCode))
			.body(new LiveErrorResponse(failureCode.name(), message(failureCode)));
	}

	@ExceptionHandler(LiveUnauthenticatedException.class)
	ResponseEntity<LiveErrorResponse> handleUnauthenticated(LiveUnauthenticatedException exception) {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
			.body(new LiveErrorResponse("UNAUTHORIZED", "인증이 필요합니다."));
	}

	private HttpStatus httpStatus(LiveTokenFailureCode failureCode) {
		return switch (failureCode) {
			case ROOM_NOT_FOUND -> HttpStatus.NOT_FOUND;
			case NOT_AUCTION_SELLER -> HttpStatus.FORBIDDEN;
		};
	}

	private String message(LiveTokenFailureCode failureCode) {
		return switch (failureCode) {
			case ROOM_NOT_FOUND -> "경매방이 존재하지 않습니다.";
			case NOT_AUCTION_SELLER -> "해당 방의 판매자만 송출할 수 있습니다.";
		};
	}
}
