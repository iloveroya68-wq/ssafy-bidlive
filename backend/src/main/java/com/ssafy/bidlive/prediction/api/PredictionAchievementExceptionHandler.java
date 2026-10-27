package com.ssafy.bidlive.prediction.api;

import com.ssafy.bidlive.prediction.api.dto.PredictionAchievementErrorResponse;
import com.ssafy.bidlive.prediction.application.PredictionAchievementException;
import com.ssafy.bidlive.prediction.domain.PredictionAchievementErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 예측 성과 조회 실패를 명세의 HTTP 상태와 오류 응답으로 변환한다. */
@RestControllerAdvice(assignableTypes = PredictionAchievementController.class)
public class PredictionAchievementExceptionHandler {

	@ExceptionHandler(PredictionAchievementException.class)
	ResponseEntity<PredictionAchievementErrorResponse> handlePredictionAchievement(
		PredictionAchievementException exception
	) {
		PredictionAchievementErrorCode errorCode = exception.getErrorCode();
		return ResponseEntity.status(httpStatus(errorCode))
			.body(new PredictionAchievementErrorResponse(
				errorCode.name(),
				message(errorCode)
			));
	}

	private HttpStatus httpStatus(PredictionAchievementErrorCode errorCode) {
		return switch (errorCode) {
			case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
			case MEMBER_NOT_FOUND -> HttpStatus.NOT_FOUND;
		};
	}

	private String message(PredictionAchievementErrorCode errorCode) {
		return switch (errorCode) {
			case UNAUTHORIZED -> "인증이 필요합니다.";
			case MEMBER_NOT_FOUND -> "인증 정보에 해당하는 회원이 존재하지 않습니다.";
		};
	}
}
