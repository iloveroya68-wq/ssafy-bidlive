package com.ssafy.bidlive.auth.api;

import com.ssafy.bidlive.auth.api.dto.AuthErrorResponse;
import com.ssafy.bidlive.auth.application.OAuthTokenExchangeException;
import com.ssafy.bidlive.auth.domain.OAuthTokenExchangeErrorCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = OAuthTokenController.class)
public class OAuthTokenExchangeExceptionHandler {

	@ExceptionHandler(OAuthTokenExchangeException.class)
	ResponseEntity<AuthErrorResponse> handleOAuthTokenExchange(
		OAuthTokenExchangeException exception
	) {
		OAuthTokenExchangeErrorCode errorCode = exception.getErrorCode();
		return ResponseEntity.status(errorCode.getHttpStatus())
			.body(new AuthErrorResponse(errorCode.name(), errorCode.getMessage()));
	}

	@ExceptionHandler({
		MethodArgumentNotValidException.class,
		HttpMessageNotReadableException.class
	})
	ResponseEntity<AuthErrorResponse> handleInvalidRequest(Exception exception) {
		OAuthTokenExchangeErrorCode errorCode =
			OAuthTokenExchangeErrorCode.INVALID_LOGIN_CODE;
		return ResponseEntity.status(errorCode.getHttpStatus())
			.body(new AuthErrorResponse(errorCode.name(), errorCode.getMessage()));
	}
}
