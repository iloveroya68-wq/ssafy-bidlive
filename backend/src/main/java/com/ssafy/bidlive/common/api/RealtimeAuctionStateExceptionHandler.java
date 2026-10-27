package com.ssafy.bidlive.common.api;

import java.time.Instant;

import com.ssafy.bidlive.common.api.dto.RealtimeAuctionStateErrorCode;
import com.ssafy.bidlive.common.api.dto.RealtimeAuctionStateErrorResponse;
import com.ssafy.bidlive.common.application.RealtimeAuctionStateQueryException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** realtime-state application 실패를 안정적인 HTTP 상태와 오류 본문으로 변환합니다. */
@RestControllerAdvice(assignableTypes = RealtimeAuctionStateController.class)
public class RealtimeAuctionStateExceptionHandler {

	@ExceptionHandler(RealtimeAuctionStateQueryException.class)
	ResponseEntity<RealtimeAuctionStateErrorResponse> handle(
		RealtimeAuctionStateQueryException exception
	) {
		HttpStatus status = switch (exception.reason()) {
			case AUCTION_NOT_FOUND -> HttpStatus.NOT_FOUND;
			case AUCTION_STATE_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
		};
		RealtimeAuctionStateErrorCode code = RealtimeAuctionStateErrorCode.valueOf(
			exception.reason().name()
		);
		String message = switch (exception.reason()) {
			case AUCTION_NOT_FOUND -> "경매를 찾을 수 없습니다.";
			case AUCTION_STATE_UNAVAILABLE ->
				"실시간 경매 상태를 일시적으로 조회할 수 없습니다.";
		};
		return ResponseEntity.status(status).body(new RealtimeAuctionStateErrorResponse(
			Instant.now(),
			status.value(),
			code,
			message
		));
	}
}
