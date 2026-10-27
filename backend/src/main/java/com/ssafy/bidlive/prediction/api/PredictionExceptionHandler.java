package com.ssafy.bidlive.prediction.api;

import com.ssafy.bidlive.prediction.api.dto.PredictionErrorResponse;
import com.ssafy.bidlive.prediction.application.PredictionRejectedException;
import com.ssafy.bidlive.prediction.domain.PredictionParticipationFailureCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** PRED-01 입력·인증·비즈니스 실패를 명세상 HTTP 오류로 변환한다. */
@RestControllerAdvice(assignableTypes = PredictionController.class)
public class PredictionExceptionHandler {

	@ExceptionHandler(PredictionRejectedException.class)
	ResponseEntity<PredictionErrorResponse> handleRejectedPrediction(
		PredictionRejectedException exception
	) {
		PredictionParticipationFailureCode failureCode =
			exception.getFailureCode();
		return ResponseEntity.status(httpStatus(failureCode))
			.body(new PredictionErrorResponse(
				failureCode.name(),
				message(failureCode)
			));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<PredictionErrorResponse> handleInvalidRequest(
		MethodArgumentNotValidException exception
	) {
		FieldError fieldError = exception.getBindingResult()
			.getFieldErrors()
			.stream()
			.findFirst()
			.orElse(null);
		return invalidRequest(fieldError == null ? null : fieldError.getField());
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	ResponseEntity<PredictionErrorResponse> handleUnreadableRequest(
		HttpMessageNotReadableException exception
	) {
		String detail = exception.getMessage();
		if (detail != null && detail.contains("predictedPrice")) {
			return invalidRequest("predictedPrice");
		}
		if (detail != null && detail.contains("stakeAmount")) {
			return invalidRequest("stakeAmount");
		}
		return invalidRequest("clientRequestId");
	}

	@ExceptionHandler(PredictionUnauthenticatedException.class)
	ResponseEntity<PredictionErrorResponse> handleUnauthenticated(
		PredictionUnauthenticatedException exception
	) {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
			.body(new PredictionErrorResponse(
				"UNAUTHORIZED",
				"인증이 필요합니다."
			));
	}

	private ResponseEntity<PredictionErrorResponse> invalidRequest(
		String field
	) {
		String code;
		String message;
		if ("predictedPrice".equals(field)) {
			code = "INVALID_PREDICTED_PRICE";
			message = "예상 낙찰가 형식 또는 범위가 올바르지 않습니다.";
		} else if ("stakeAmount".equals(field)) {
			code = "INVALID_STAKE_AMOUNT";
			message = "베팅 포인트 형식 또는 범위가 올바르지 않습니다.";
		} else {
			code = "INVALID_CLIENT_REQUEST_ID";
			message = "clientRequestId 형식이 올바르지 않습니다.";
		}
		return ResponseEntity.badRequest()
			.body(new PredictionErrorResponse(code, message));
	}

	private HttpStatus httpStatus(
		PredictionParticipationFailureCode failureCode
	) {
		return switch (failureCode) {
			case SELLER_CANNOT_PREDICT,
				BIDDER_CANNOT_PREDICT -> HttpStatus.FORBIDDEN;
			case AUCTION_NOT_FOUND -> HttpStatus.NOT_FOUND;
			case AUCTION_NOT_LIVE,
				PREDICTION_CLOSED,
				PREDICTION_ALREADY_EXISTS,
				INSUFFICIENT_BETTING_POINTS,
				IDEMPOTENCY_KEY_REUSED -> HttpStatus.CONFLICT;
		};
	}

	private String message(
		PredictionParticipationFailureCode failureCode
	) {
		return switch (failureCode) {
			case AUCTION_NOT_FOUND -> "경매가 존재하지 않습니다.";
			case AUCTION_NOT_LIVE -> "예측 가능한 경매 상태가 아닙니다.";
			case PREDICTION_CLOSED -> "예측 접수 시간이 종료되었습니다.";
			case SELLER_CANNOT_PREDICT ->
				"판매자는 자신의 경매를 예측할 수 없습니다.";
			case BIDDER_CANNOT_PREDICT ->
				"입찰에 참여한 경매는 예측할 수 없습니다.";
			case PREDICTION_ALREADY_EXISTS ->
				"해당 경매에 이미 예측을 등록했습니다.";
			case INSUFFICIENT_BETTING_POINTS ->
				"사용 가능한 베팅 포인트가 부족합니다.";
			case IDEMPOTENCY_KEY_REUSED ->
				"clientRequestId가 다른 요청에 사용되었습니다.";
		};
	}
}
