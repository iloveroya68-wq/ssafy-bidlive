package com.ssafy.bidlive.wallet.api;

import com.ssafy.bidlive.wallet.api.dto.TradePointRefundErrorResponse;
import com.ssafy.bidlive.wallet.application.TradePointRefundErrorCode;
import com.ssafy.bidlive.wallet.application.TradePointRefundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 거래 포인트 환불 실패를 명세의 HTTP 상태와 오류 코드로 변환한다. */
@RestControllerAdvice(assignableTypes = TradePointRefundController.class)
public class TradePointRefundExceptionHandler {

	@ExceptionHandler(HttpMessageNotReadableException.class)
	ResponseEntity<TradePointRefundErrorResponse> handleMalformedBody(
		HttpMessageNotReadableException exception
	) {
		return response(TradePointRefundErrorCode.INVALID_REFUND_AMOUNT);
	}

	@ExceptionHandler(TradePointRefundException.class)
	ResponseEntity<TradePointRefundErrorResponse> handle(
		TradePointRefundException exception
	) {
		return response(exception.getErrorCode());
	}

	private ResponseEntity<TradePointRefundErrorResponse> response(
		TradePointRefundErrorCode errorCode
	) {
		return ResponseEntity.status(httpStatus(errorCode))
			.body(new TradePointRefundErrorResponse(
				errorCode.name(),
				message(errorCode)
			));
	}

	private HttpStatus httpStatus(
		TradePointRefundErrorCode errorCode
	) {
		return switch (errorCode) {
			case INVALID_REFUND_AMOUNT -> HttpStatus.BAD_REQUEST;
			case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
			case TRADE_WALLET_NOT_FOUND -> HttpStatus.NOT_FOUND;
			case DUPLICATE_REQUEST -> HttpStatus.CONFLICT;
			case INSUFFICIENT_AVAILABLE_AMOUNT ->
				HttpStatus.UNPROCESSABLE_ENTITY;
		};
	}

	private String message(
		TradePointRefundErrorCode errorCode
	) {
		return switch (errorCode) {
			case INVALID_REFUND_AMOUNT ->
				"환불 금액 형식 또는 범위를 확인해 주세요.";
			case UNAUTHORIZED -> "인증이 필요합니다.";
			case TRADE_WALLET_NOT_FOUND ->
				"거래 포인트 지갑이 존재하지 않습니다.";
			case DUPLICATE_REQUEST -> "이미 처리된 환불 요청입니다.";
			case INSUFFICIENT_AVAILABLE_AMOUNT ->
				"환불 가능한 가용 포인트가 부족합니다.";
		};
	}
}
