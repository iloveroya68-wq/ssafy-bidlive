package com.ssafy.bidlive.member.api;

import com.ssafy.bidlive.member.api.dto.NicknameAvailabilityErrorResponse;
import com.ssafy.bidlive.member.application.NicknameAvailabilityException;
import com.ssafy.bidlive.member.domain.NicknameAvailabilityErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 닉네임 확인 요청 오류를 명세의 HTTP 상태와 응답 코드로 변환한다. */
@RestControllerAdvice(assignableTypes = NicknameAvailabilityController.class)
public class NicknameAvailabilityExceptionHandler {

	@ExceptionHandler(NicknameAvailabilityException.class)
	ResponseEntity<NicknameAvailabilityErrorResponse> handle(
		NicknameAvailabilityException exception
	) {
		NicknameAvailabilityErrorCode errorCode = exception.getErrorCode();
		return ResponseEntity.status(httpStatus(errorCode))
			.body(new NicknameAvailabilityErrorResponse(
				errorCode.name(),
				message(errorCode)
			));
	}

	private HttpStatus httpStatus(NicknameAvailabilityErrorCode errorCode) {
		return switch (errorCode) {
			case NICKNAME_REQUIRED -> HttpStatus.BAD_REQUEST;
			case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
		};
	}

	private String message(NicknameAvailabilityErrorCode errorCode) {
		return switch (errorCode) {
			case NICKNAME_REQUIRED -> "닉네임을 입력해 주세요.";
			case UNAUTHORIZED -> "인증이 필요합니다.";
		};
	}
}
