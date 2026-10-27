package com.ssafy.bidlive.prediction.api;

import com.ssafy.bidlive.prediction.api.dto.PredictionErrorResponse;
import com.ssafy.bidlive.prediction.application.PredictionLeaderboardQueryException;
import com.ssafy.bidlive.prediction.domain.PredictionLeaderboardQueryErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** PRED-05 입력·인증 실패를 명세의 HTTP 오류 응답으로 변환한다. */
@RestControllerAdvice(assignableTypes = PredictionLeaderboardController.class)
public class PredictionLeaderboardExceptionHandler {

	@ExceptionHandler(PredictionLeaderboardQueryException.class)
	ResponseEntity<PredictionErrorResponse> handleQueryFailure(
		PredictionLeaderboardQueryException exception
	) {
		return response(exception.getErrorCode());
	}

	@ExceptionHandler(BindException.class)
	ResponseEntity<PredictionErrorResponse> handleInvalidQuery(
		BindException exception
	) {
		FieldError fieldError = exception.getBindingResult()
			.getFieldErrors()
			.stream()
			.findFirst()
			.orElse(null);
		PredictionLeaderboardQueryErrorCode errorCode = fieldError != null
			&& "size".equals(fieldError.getField())
			? PredictionLeaderboardQueryErrorCode.INVALID_PAGE_SIZE
			: PredictionLeaderboardQueryErrorCode.INVALID_RANKING_DATE;
		return response(errorCode);
	}

	private ResponseEntity<PredictionErrorResponse> response(
		PredictionLeaderboardQueryErrorCode errorCode
	) {
		return ResponseEntity.status(httpStatus(errorCode))
			.body(new PredictionErrorResponse(
				errorCode.name(),
				message(errorCode)
			));
	}

	private HttpStatus httpStatus(
		PredictionLeaderboardQueryErrorCode errorCode
	) {
		return errorCode == PredictionLeaderboardQueryErrorCode.UNAUTHORIZED
			? HttpStatus.UNAUTHORIZED
			: HttpStatus.BAD_REQUEST;
	}

	private String message(PredictionLeaderboardQueryErrorCode errorCode) {
		return switch (errorCode) {
			case INVALID_RANKING_DATE -> "조회 날짜가 올바르지 않습니다.";
			case INVALID_PAGE_SIZE -> "조회 크기는 1 이상 100 이하여야 합니다.";
			case INVALID_CURSOR -> "리더보드 커서가 올바르지 않습니다.";
			case UNAUTHORIZED -> "인증이 필요합니다.";
		};
	}
}
