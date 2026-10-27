package com.ssafy.bidlive.member.api;

import com.ssafy.bidlive.member.api.dto.TermsErrorResponse;
import com.ssafy.bidlive.member.application.TermsAgreementException;
import com.ssafy.bidlive.member.domain.TermsAgreementErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 약관 동의 실패를 명세에 정의된 HTTP 상태와 오류 응답으로 변환한다. */
@RestControllerAdvice(assignableTypes = TermsAgreementController.class)
public class TermsAgreementExceptionHandler {

	@ExceptionHandler(TermsAgreementException.class)
	ResponseEntity<TermsErrorResponse> handleTermsAgreement(
		TermsAgreementException exception
	) {
		TermsAgreementErrorCode errorCode = exception.getErrorCode();
		return ResponseEntity.status(httpStatus(errorCode))
			.body(new TermsErrorResponse(errorCode.name(), message(errorCode)));
	}

	@ExceptionHandler({
		MethodArgumentNotValidException.class,
		HttpMessageNotReadableException.class
	})
	ResponseEntity<TermsErrorResponse> handleInvalidRequest(Exception exception) {
		return ResponseEntity.badRequest()
			.body(new TermsErrorResponse(
				"INVALID_TERMS_REQUEST",
				"약관 동의 요청 형식이 올바르지 않습니다."
			));
	}

	private HttpStatus httpStatus(TermsAgreementErrorCode errorCode) {
		return switch (errorCode) {
			case INVALID_TERMS_REQUEST, DUPLICATE_TERM_ID -> HttpStatus.BAD_REQUEST;
			case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
			case MEMBER_NOT_FOUND, TERM_NOT_FOUND -> HttpStatus.NOT_FOUND;
			case REQUIRED_TERMS_NOT_AGREED,
				TERMS_VERSION_OUTDATED,
				TERM_NOT_ACTIVE -> HttpStatus.CONFLICT;
			case TERMS_AGREEMENT_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
		};
	}

	private String message(TermsAgreementErrorCode errorCode) {
		return switch (errorCode) {
			case INVALID_TERMS_REQUEST -> "약관 동의 요청 형식이 올바르지 않습니다.";
			case DUPLICATE_TERM_ID -> "동일한 약관 ID가 중복 포함되어 있습니다.";
			case UNAUTHORIZED -> "인증이 필요합니다.";
			case MEMBER_NOT_FOUND -> "인증 정보에 해당하는 회원이 존재하지 않습니다.";
			case TERM_NOT_FOUND -> "요청한 약관이 존재하지 않습니다.";
			case REQUIRED_TERMS_NOT_AGREED -> "모든 필수 약관에 동의해야 합니다.";
			case TERMS_VERSION_OUTDATED -> "현재 적용 중인 약관 버전이 아닙니다.";
			case TERM_NOT_ACTIVE -> "현재 동의할 수 있는 약관이 아닙니다.";
			case TERMS_AGREEMENT_UNAVAILABLE -> "약관 동의를 처리할 수 없습니다.";
		};
	}
}
