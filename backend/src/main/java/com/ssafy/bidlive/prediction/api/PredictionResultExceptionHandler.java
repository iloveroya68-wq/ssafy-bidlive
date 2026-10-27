package com.ssafy.bidlive.prediction.api;

import com.ssafy.bidlive.prediction.api.dto.PredictionResultErrorResponse;
import com.ssafy.bidlive.prediction.application.PredictionResultQueryException;
import com.ssafy.bidlive.prediction.domain.PredictionResultQueryErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 예측 결과 조회 실패를 명세의 HTTP 상태와 오류 응답으로 변환한다. */
@RestControllerAdvice(assignableTypes = PredictionResultController.class)
public class PredictionResultExceptionHandler {

	@ExceptionHandler(PredictionResultQueryException.class)
	ResponseEntity<PredictionResultErrorResponse> handlePredictionResultQuery(
		PredictionResultQueryException exception
	) {
		PredictionResultQueryErrorCode errorCode = exception.getErrorCode();
		return ResponseEntity.status(httpStatus(errorCode))
			.body(new PredictionResultErrorResponse(
				errorCode.name(),
				message(errorCode)
			));
	}

	private HttpStatus httpStatus(
		PredictionResultQueryErrorCode errorCode
	) {
		return switch (errorCode) {
			case INVALID_AUCTION_ID -> HttpStatus.BAD_REQUEST;
			case AUCTION_NOT_FOUND -> HttpStatus.NOT_FOUND;
			case PREDICTION_RESULT_NOT_AVAILABLE -> HttpStatus.CONFLICT;
		};
	}

	private String message(
		PredictionResultQueryErrorCode errorCode
	) {
		return switch (errorCode) {
			case INVALID_AUCTION_ID -> "경매 ID가 올바르지 않습니다.";
			case AUCTION_NOT_FOUND -> "경매가 존재하지 않습니다.";
			case PREDICTION_RESULT_NOT_AVAILABLE ->
				"예측 결과를 아직 조회할 수 없습니다.";
		};
	}
}
