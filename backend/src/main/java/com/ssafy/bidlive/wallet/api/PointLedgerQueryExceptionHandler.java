package com.ssafy.bidlive.wallet.api;

import com.ssafy.bidlive.wallet.api.dto.PointLedgerQueryErrorResponse;
import com.ssafy.bidlive.wallet.application.PointLedgerQueryErrorCode;
import com.ssafy.bidlive.wallet.application.PointLedgerQueryException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 포인트 원장 조회 실패를 명세의 HTTP 상태와 오류 응답으로 변환한다. */
@RestControllerAdvice(assignableTypes = PointLedgerQueryController.class)
public class PointLedgerQueryExceptionHandler {

	@ExceptionHandler(PointLedgerQueryException.class)
	ResponseEntity<PointLedgerQueryErrorResponse> handle(
		PointLedgerQueryException exception
	) {
		PointLedgerQueryErrorCode errorCode = exception.getErrorCode();
		return ResponseEntity.status(httpStatus(errorCode))
			.body(new PointLedgerQueryErrorResponse(
				errorCode.name(),
				message(errorCode)
			));
	}

	private HttpStatus httpStatus(PointLedgerQueryErrorCode errorCode) {
		return switch (errorCode) {
			case INVALID_CURSOR, INVALID_LEDGER_FILTER ->
				HttpStatus.BAD_REQUEST;
			case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
			case WALLET_NOT_FOUND -> HttpStatus.NOT_FOUND;
		};
	}

	private String message(PointLedgerQueryErrorCode errorCode) {
		return switch (errorCode) {
			case INVALID_CURSOR -> "커서 형식이 올바르지 않습니다.";
			case INVALID_LEDGER_FILTER ->
				"지원하지 않는 포인트 원장 조회 조건입니다.";
			case UNAUTHORIZED -> "인증이 필요합니다.";
			case WALLET_NOT_FOUND ->
				"회원의 필수 포인트 지갑이 존재하지 않습니다.";
		};
	}
}
