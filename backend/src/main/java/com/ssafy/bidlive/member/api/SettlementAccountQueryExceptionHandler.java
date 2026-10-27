package com.ssafy.bidlive.member.api;

import com.ssafy.bidlive.member.api.dto.SettlementAccountQueryErrorResponse;
import com.ssafy.bidlive.member.application.SettlementAccountQueryException;
import com.ssafy.bidlive.member.domain.SettlementAccountQueryErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 정산 계좌 조회 실패를 명세의 HTTP 상태와 응답으로 변환한다. */
@RestControllerAdvice(
	assignableTypes = SettlementAccountQueryController.class
)
public class SettlementAccountQueryExceptionHandler {

	@ExceptionHandler(SettlementAccountQueryException.class)
	ResponseEntity<SettlementAccountQueryErrorResponse> handle(
		SettlementAccountQueryException exception
	) {
		SettlementAccountQueryErrorCode errorCode = exception.getErrorCode();
		return ResponseEntity.status(httpStatus(errorCode))
			.body(new SettlementAccountQueryErrorResponse(
				errorCode.name(),
				message(errorCode)
			));
	}

	private HttpStatus httpStatus(
		SettlementAccountQueryErrorCode errorCode
	) {
		return switch (errorCode) {
			case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
			case MEMBER_NOT_FOUND -> HttpStatus.NOT_FOUND;
			case SETTLEMENT_ACCOUNT_UNAVAILABLE ->
				HttpStatus.SERVICE_UNAVAILABLE;
		};
	}

	private String message(SettlementAccountQueryErrorCode errorCode) {
		return switch (errorCode) {
			case UNAUTHORIZED -> "인증이 필요합니다.";
			case MEMBER_NOT_FOUND ->
				"인증 정보에 해당하는 회원이 존재하지 않습니다.";
			case SETTLEMENT_ACCOUNT_UNAVAILABLE ->
				"정산 계좌 정보를 조회하지 못했습니다.";
		};
	}
}
