package com.ssafy.bidlive.wallet.api;

import com.ssafy.bidlive.wallet.api.dto.TradePointChargeErrorResponse;
import com.ssafy.bidlive.wallet.application.TradePointChargeErrorCode;
import com.ssafy.bidlive.wallet.application.TradePointChargeException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 거래 포인트 충전 실패를 명세의 HTTP 상태와 오류 코드로 변환한다. */
@RestControllerAdvice(assignableTypes = TradePointChargeController.class)
public class TradePointChargeExceptionHandler {

	@ExceptionHandler(HttpMessageNotReadableException.class)
	ResponseEntity<TradePointChargeErrorResponse> handleMalformedBody(
		HttpMessageNotReadableException exception
	) {
		return response(TradePointChargeErrorCode.INVALID_CHARGE_AMOUNT);
	}

	@ExceptionHandler(TradePointChargeException.class)
	ResponseEntity<TradePointChargeErrorResponse> handle(
		TradePointChargeException exception
	) {
		return response(exception.getErrorCode());
	}

	private ResponseEntity<TradePointChargeErrorResponse> response(
		TradePointChargeErrorCode errorCode
	) {
		return ResponseEntity.status(httpStatus(errorCode))
			.body(new TradePointChargeErrorResponse(
				errorCode.name(),
				message(errorCode)
			));
	}

	private HttpStatus httpStatus(
		TradePointChargeErrorCode errorCode
	) {
		return switch (errorCode) {
			case INVALID_CHARGE_AMOUNT -> HttpStatus.BAD_REQUEST;
			case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
			case TRADE_WALLET_NOT_FOUND -> HttpStatus.NOT_FOUND;
			case DUPLICATE_REQUEST -> HttpStatus.CONFLICT;
			case CHARGE_LIMIT_EXCEEDED ->
				HttpStatus.UNPROCESSABLE_ENTITY;
		};
	}

	private String message(
		TradePointChargeErrorCode errorCode
	) {
		return switch (errorCode) {
			case INVALID_CHARGE_AMOUNT ->
				"충전 금액 형식 또는 범위를 확인해 주세요.";
			case UNAUTHORIZED -> "인증이 필요합니다.";
			case TRADE_WALLET_NOT_FOUND ->
				"거래 포인트 지갑이 존재하지 않습니다.";
			case DUPLICATE_REQUEST -> "이미 처리된 충전 요청입니다.";
			case CHARGE_LIMIT_EXCEEDED ->
				"1회 또는 일일 충전 한도를 초과했습니다.";
		};
	}
}
