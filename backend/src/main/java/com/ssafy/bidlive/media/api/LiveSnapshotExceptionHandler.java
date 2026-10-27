package com.ssafy.bidlive.media.api;

import com.ssafy.bidlive.media.api.dto.LiveErrorResponse;
import com.ssafy.bidlive.media.application.LiveSnapshotRejectedException;
import com.ssafy.bidlive.media.domain.LiveSnapshotFailureCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

/** LIVE-06의 입력·권한·상태·저장소 실패를 명세상 HTTP 오류로 변환합니다. */
@RestControllerAdvice(assignableTypes = LiveSnapshotController.class)
public class LiveSnapshotExceptionHandler {

	@ExceptionHandler(LiveSnapshotRejectedException.class)
	ResponseEntity<LiveErrorResponse> handleRejectedSnapshot(
		LiveSnapshotRejectedException exception
	) {
		LiveSnapshotFailureCode failureCode = exception.getFailureCode();
		return ResponseEntity.status(httpStatus(failureCode))
			.body(new LiveErrorResponse(failureCode.name(), message(failureCode)));
	}

	@ExceptionHandler(LiveUnauthenticatedException.class)
	ResponseEntity<LiveErrorResponse> handleUnauthenticated(LiveUnauthenticatedException exception) {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
			.body(new LiveErrorResponse("UNAUTHORIZED", "인증이 필요합니다."));
	}

	/** 필수 파트 누락과 auctionId 형식 오류는 같은 입력 오류로 다룬다. */
	@ExceptionHandler({
		MissingServletRequestPartException.class,
		MissingServletRequestParameterException.class,
		MethodArgumentTypeMismatchException.class
	})
	ResponseEntity<LiveErrorResponse> handleMissingPart(Exception exception) {
		return ResponseEntity.badRequest()
			.body(new LiveErrorResponse(
				"INVALID_SNAPSHOT_REQUEST",
				"image 파일과 auctionId 값이 모두 필요합니다."
			));
	}

	/**
	 * multipart 지연 해석 설정 덕분에 컨테이너 한도 초과도 이 핸들러 범위에서 잡힌다.
	 * 도메인 크기 정책 초과와 같은 상태 코드로 응답한다.
	 */
	@ExceptionHandler(MaxUploadSizeExceededException.class)
	ResponseEntity<LiveErrorResponse> handleUploadSizeExceeded(
		MaxUploadSizeExceededException exception
	) {
		return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
			.body(new LiveErrorResponse(
				LiveSnapshotFailureCode.IMAGE_TOO_LARGE.name(),
				message(LiveSnapshotFailureCode.IMAGE_TOO_LARGE)
			));
	}

	private HttpStatus httpStatus(LiveSnapshotFailureCode failureCode) {
		return switch (failureCode) {
			case IMAGE_REQUIRED, UNSUPPORTED_IMAGE_TYPE -> HttpStatus.BAD_REQUEST;
			case IMAGE_TOO_LARGE -> HttpStatus.PAYLOAD_TOO_LARGE;
			case NOT_SNAPSHOT_UPLOADER -> HttpStatus.FORBIDDEN;
			case AUCTION_NOT_FOUND -> HttpStatus.NOT_FOUND;
			case AUCTION_NOT_SETTLED -> HttpStatus.CONFLICT;
			case SNAPSHOT_STORAGE_ERROR -> HttpStatus.BAD_GATEWAY;
		};
	}

	private String message(LiveSnapshotFailureCode failureCode) {
		return switch (failureCode) {
			case IMAGE_REQUIRED -> "스냅샷 이미지가 필요합니다.";
			case UNSUPPORTED_IMAGE_TYPE -> "JPEG 또는 PNG 이미지만 업로드할 수 있습니다.";
			case IMAGE_TOO_LARGE -> "스냅샷 이미지 크기가 허용 범위를 초과했습니다.";
			case AUCTION_NOT_FOUND -> "방 또는 경매가 존재하지 않습니다.";
			case NOT_SNAPSHOT_UPLOADER -> "이 경매의 스냅샷을 업로드할 자격이 없습니다.";
			case AUCTION_NOT_SETTLED -> "낙찰이 확정된 경매가 아닙니다.";
			case SNAPSHOT_STORAGE_ERROR -> "스냅샷 저장소 연동에 실패했습니다.";
		};
	}
}
