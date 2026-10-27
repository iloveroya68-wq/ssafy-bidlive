package com.ssafy.bidlive.member.api;

import com.ssafy.bidlive.member.api.dto.TermsErrorResponse;
import com.ssafy.bidlive.member.application.TermsQueryException;
import com.ssafy.bidlive.member.domain.TermsQueryErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 약관 조회 실패를 명세에 정의된 HTTP 상태와 오류 응답으로 변환한다. */
@RestControllerAdvice(assignableTypes = TermsController.class)
public class TermsExceptionHandler {

	@ExceptionHandler(TermsQueryException.class)
	ResponseEntity<TermsErrorResponse> handleTermsQuery(TermsQueryException exception) {
		TermsQueryErrorCode errorCode = exception.getErrorCode();
		return ResponseEntity.status(httpStatus(errorCode))
			.body(new TermsErrorResponse(errorCode.name(), message(errorCode)));
	}

	private HttpStatus httpStatus(TermsQueryErrorCode errorCode) {
		return switch (errorCode) {
			case INVALID_TERMS_PURPOSE -> HttpStatus.BAD_REQUEST;
			case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
			case ACTIVE_TERMS_NOT_FOUND, MEMBER_NOT_FOUND -> HttpStatus.NOT_FOUND;
			case TERMS_SERVICE_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
		};
	}

	private String message(TermsQueryErrorCode errorCode) {
		return switch (errorCode) {
			case INVALID_TERMS_PURPOSE -> "지원하지 않는 약관 조회 목적입니다.";
			case UNAUTHORIZED -> "인증이 필요합니다.";
			case ACTIVE_TERMS_NOT_FOUND -> "현재 적용 중인 약관이 존재하지 않습니다.";
			case MEMBER_NOT_FOUND -> "인증 정보에 해당하는 회원이 존재하지 않습니다.";
			case TERMS_SERVICE_UNAVAILABLE -> "약관 정보를 조회할 수 없습니다.";
		};
	}
}
