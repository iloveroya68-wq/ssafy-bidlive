package com.ssafy.bidlive.wallet.api;

import com.ssafy.bidlive.wallet.api.dto.WalletQueryErrorResponse;
import com.ssafy.bidlive.wallet.application.WalletQueryErrorCode;
import com.ssafy.bidlive.wallet.application.WalletQueryException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 지갑 조회 실패를 명세의 HTTP 상태와 응답으로 변환한다. */
@RestControllerAdvice(assignableTypes = WalletQueryController.class)
public class WalletQueryExceptionHandler {

	@ExceptionHandler(WalletQueryException.class)
	ResponseEntity<WalletQueryErrorResponse> handle(
		WalletQueryException exception
	) {
		WalletQueryErrorCode errorCode = exception.getErrorCode();
		return ResponseEntity.status(httpStatus(errorCode))
			.body(new WalletQueryErrorResponse(
				errorCode.name(),
				message(errorCode)
			));
	}

	private HttpStatus httpStatus(WalletQueryErrorCode errorCode) {
		return switch (errorCode) {
			case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
			case MEMBER_NOT_FOUND, WALLET_NOT_FOUND ->
				HttpStatus.NOT_FOUND;
		};
	}

	private String message(WalletQueryErrorCode errorCode) {
		return switch (errorCode) {
			case UNAUTHORIZED -> "인증이 필요합니다.";
			case MEMBER_NOT_FOUND ->
				"인증 정보에 해당하는 회원이 존재하지 않습니다.";
			case WALLET_NOT_FOUND ->
				"회원의 필수 포인트 지갑이 존재하지 않습니다.";
		};
	}
}
