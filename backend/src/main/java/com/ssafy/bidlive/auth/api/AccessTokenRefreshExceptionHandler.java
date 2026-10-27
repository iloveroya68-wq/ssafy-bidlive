package com.ssafy.bidlive.auth.api;

import com.ssafy.bidlive.auth.api.dto.AuthErrorResponse;
import com.ssafy.bidlive.auth.application.AccessTokenRefreshException;
import com.ssafy.bidlive.auth.domain.AccessTokenRefreshErrorCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Access Token 재발급 실패를 명세의 HTTP 상태와 서비스 오류 코드로 변환한다. */
@RestControllerAdvice(assignableTypes = AccessTokenRefreshController.class)
public class AccessTokenRefreshExceptionHandler {

	@ExceptionHandler(AccessTokenRefreshException.class)
	ResponseEntity<AuthErrorResponse> handleAccessTokenRefresh(
		AccessTokenRefreshException exception
	) {
		AccessTokenRefreshErrorCode errorCode = exception.getErrorCode();
		return ResponseEntity.status(errorCode.getHttpStatus())
			.body(new AuthErrorResponse(errorCode.name(), errorCode.getMessage()));
	}
}
