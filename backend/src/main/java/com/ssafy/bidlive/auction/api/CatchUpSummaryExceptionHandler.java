package com.ssafy.bidlive.auction.api;

import com.ssafy.bidlive.auction.api.dto.CatchUpSummaryErrorResponse;
import com.ssafy.bidlive.auction.application.CatchUpSummaryException;
import com.ssafy.bidlive.auction.domain.CatchUpSummaryErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** AI-03 실패를 명세와 음성 수집 계약의 HTTP 상태로 변환한다. */
@RestControllerAdvice(assignableTypes = {
	CatchUpSummaryController.class,
	CatchUpAudioChunkController.class
})
public class CatchUpSummaryExceptionHandler {

	@ExceptionHandler(CatchUpSummaryException.class)
	public ResponseEntity<CatchUpSummaryErrorResponse> handle(
		CatchUpSummaryException exception
	) {
		CatchUpSummaryErrorCode code = exception.code();
		return ResponseEntity.status(status(code))
			.body(new CatchUpSummaryErrorResponse(code.name(), message(code)));
	}

	private static HttpStatus status(CatchUpSummaryErrorCode code) {
		return switch (code) {
			case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
			case ROOM_ACCESS_DENIED, NOT_AUCTION_SELLER -> HttpStatus.FORBIDDEN;
			case AUCTION_ROOM_NOT_FOUND -> HttpStatus.NOT_FOUND;
			case AUCTION_NOT_STARTED -> HttpStatus.CONFLICT;
			case INVALID_AUDIO_CHUNK -> HttpStatus.BAD_REQUEST;
			case UNSUPPORTED_AUDIO_TYPE -> HttpStatus.UNSUPPORTED_MEDIA_TYPE;
			case AUDIO_CHUNK_TOO_LARGE -> HttpStatus.PAYLOAD_TOO_LARGE;
		};
	}

	private static String message(CatchUpSummaryErrorCode code) {
		return switch (code) {
			case UNAUTHORIZED -> "인증이 필요합니다.";
			case ROOM_ACCESS_DENIED -> "경매방에 접근할 수 없습니다.";
			case AUCTION_ROOM_NOT_FOUND -> "경매방이 존재하지 않습니다.";
			case AUCTION_NOT_STARTED -> "아직 방송이 시작되지 않았습니다.";
			case NOT_AUCTION_SELLER -> "판매자만 방송 음성을 전송할 수 있습니다.";
			case INVALID_AUDIO_CHUNK -> "음성 청크 형식이 올바르지 않습니다.";
			case UNSUPPORTED_AUDIO_TYPE -> "지원하지 않는 음성 형식입니다.";
			case AUDIO_CHUNK_TOO_LARGE -> "음성 청크 크기가 제한을 초과했습니다.";
		};
	}
}
