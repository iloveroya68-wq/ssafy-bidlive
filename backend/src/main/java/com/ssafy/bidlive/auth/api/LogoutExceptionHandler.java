package com.ssafy.bidlive.auth.api;

import com.ssafy.bidlive.auth.api.dto.AuthErrorResponse;
import com.ssafy.bidlive.auth.application.LogoutException;
import com.ssafy.bidlive.auth.domain.LogoutErrorCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 로그아웃 실패를 명세의 HTTP 상태와 서비스 오류 코드로 변환한다. */
@RestControllerAdvice(assignableTypes = LogoutController.class)
public class LogoutExceptionHandler {

	@ExceptionHandler(LogoutException.class)
	ResponseEntity<AuthErrorResponse> handleLogout(LogoutException exception) {
		LogoutErrorCode errorCode = exception.getErrorCode();
		return ResponseEntity.status(errorCode.getHttpStatus())
			.body(new AuthErrorResponse(errorCode.name(), errorCode.getMessage()));
	}
}
