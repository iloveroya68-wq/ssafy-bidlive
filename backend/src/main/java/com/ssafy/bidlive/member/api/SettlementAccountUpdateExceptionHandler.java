package com.ssafy.bidlive.member.api;

import com.ssafy.bidlive.member.api.dto.SettlementAccountUpdateErrorResponse;
import com.ssafy.bidlive.member.application.SettlementAccountUpdateException;
import com.ssafy.bidlive.member.domain.SettlementAccountUpdateErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 정산 계좌 등록·변경 실패를 명세의 HTTP 오류로 변환한다. */
@RestControllerAdvice(assignableTypes = SettlementAccountUpdateController.class)
public class SettlementAccountUpdateExceptionHandler {

	@ExceptionHandler(HttpMessageNotReadableException.class)
	ResponseEntity<SettlementAccountUpdateErrorResponse> handleMalformedBody(
		HttpMessageNotReadableException exception
	) {
		return response(
			SettlementAccountUpdateErrorCode.INVALID_ACCOUNT_NUMBER
		);
	}

	@ExceptionHandler(SettlementAccountUpdateException.class)
	ResponseEntity<SettlementAccountUpdateErrorResponse> handle(
		SettlementAccountUpdateException exception
	) {
		return response(exception.getErrorCode());
	}

	private ResponseEntity<SettlementAccountUpdateErrorResponse> response(
		SettlementAccountUpdateErrorCode errorCode
	) {
		return ResponseEntity.status(httpStatus(errorCode))
			.body(new SettlementAccountUpdateErrorResponse(
				errorCode.name(),
				message(errorCode)
			));
	}

	private HttpStatus httpStatus(
		SettlementAccountUpdateErrorCode errorCode
	) {
		return switch (errorCode) {
			case INVALID_BANK_CODE, INVALID_ACCOUNT_NUMBER ->
				HttpStatus.BAD_REQUEST;
			case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
			case MEMBER_NOT_FOUND -> HttpStatus.NOT_FOUND;
			case SETTLEMENT_ACCOUNT_CHANGE_BLOCKED -> HttpStatus.CONFLICT;
			case SETTLEMENT_ACCOUNT_UPDATE_FAILED ->
				HttpStatus.SERVICE_UNAVAILABLE;
		};
	}

	private String message(
		SettlementAccountUpdateErrorCode errorCode
	) {
		return switch (errorCode) {
			case INVALID_BANK_CODE -> "지원하지 않는 은행 코드입니다.";
			case INVALID_ACCOUNT_NUMBER ->
				"계좌번호 또는 예금주명 형식을 확인해 주세요.";
			case UNAUTHORIZED -> "인증이 필요합니다.";
			case MEMBER_NOT_FOUND ->
				"인증 정보에 해당하는 회원이 존재하지 않습니다.";
			case SETTLEMENT_ACCOUNT_CHANGE_BLOCKED ->
				"진행 중인 정산으로 계좌를 변경할 수 없습니다.";
			case SETTLEMENT_ACCOUNT_UPDATE_FAILED ->
				"정산 계좌 정보를 저장하지 못했습니다.";
		};
	}
}
