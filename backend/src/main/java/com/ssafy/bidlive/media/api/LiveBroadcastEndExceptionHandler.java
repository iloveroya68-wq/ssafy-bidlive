package com.ssafy.bidlive.media.api;

import com.ssafy.bidlive.media.api.dto.LiveErrorResponse;
import com.ssafy.bidlive.media.application.LiveBroadcastEndRejectedException;
import com.ssafy.bidlive.media.domain.LiveBroadcastEndFailureCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** LIVE-02의 인증·권한·상태·미디어 서버 실패를 명세상 HTTP 오류로 변환합니다. */
@RestControllerAdvice(assignableTypes = LiveBroadcastEndController.class)
public class LiveBroadcastEndExceptionHandler {

	@ExceptionHandler(LiveBroadcastEndRejectedException.class)
	ResponseEntity<LiveErrorResponse> handleRejectedEnd(
		LiveBroadcastEndRejectedException exception
	) {
		LiveBroadcastEndFailureCode failureCode = exception.getFailureCode();
		return ResponseEntity.status(httpStatus(failureCode))
			.body(new LiveErrorResponse(failureCode.name(), message(failureCode)));
	}

	@ExceptionHandler(LiveUnauthenticatedException.class)
	ResponseEntity<LiveErrorResponse> handleUnauthenticated(LiveUnauthenticatedException exception) {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
			.body(new LiveErrorResponse("UNAUTHORIZED", "인증이 필요합니다."));
	}

	private HttpStatus httpStatus(LiveBroadcastEndFailureCode failureCode) {
		return switch (failureCode) {
			case LIVE_NOT_FOUND -> HttpStatus.NOT_FOUND;
			case NOT_BROADCAST_OWNER -> HttpStatus.FORBIDDEN;
			case LIVE_NOT_ACTIVE -> HttpStatus.CONFLICT;
			case MEDIA_SERVER_ERROR -> HttpStatus.BAD_GATEWAY;
		};
	}

	private String message(LiveBroadcastEndFailureCode failureCode) {
		return switch (failureCode) {
			case LIVE_NOT_FOUND -> "방송 기록이 존재하지 않습니다.";
			case NOT_BROADCAST_OWNER -> "방송을 시작한 판매자만 종료할 수 있습니다.";
			case LIVE_NOT_ACTIVE -> "진행 중인 방송이 아닙니다.";
			case MEDIA_SERVER_ERROR -> "미디어 서버 연동에 실패했습니다.";
		};
	}
}
