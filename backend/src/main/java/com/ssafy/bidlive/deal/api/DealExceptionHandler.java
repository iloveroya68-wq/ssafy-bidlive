package com.ssafy.bidlive.deal.api;

import com.ssafy.bidlive.deal.api.dto.DealErrorResponse;
import com.ssafy.bidlive.deal.application.DealException;
import com.ssafy.bidlive.deal.domain.DealErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 거래 API의 실패를 오류 코드와 HTTP 상태로 변환한다. */
@RestControllerAdvice(assignableTypes = {
	DealTodoController.class,
	DealEscrowController.class,
	MyDealListController.class,
	DealShippingController.class
})
public class DealExceptionHandler {

	@ExceptionHandler(DealException.class)
	ResponseEntity<DealErrorResponse> handleDeal(DealException exception) {
		DealErrorCode errorCode = exception.getErrorCode();
		return ResponseEntity.status(statusOf(errorCode))
			.body(new DealErrorResponse(errorCode.name(), messageOf(errorCode)));
	}

	private static HttpStatus statusOf(DealErrorCode errorCode) {
		return switch (errorCode) {
			case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
			case INVALID_SHIPPING_ADDRESS, INVALID_DEAL_FILTER, INVALID_CURSOR -> HttpStatus.BAD_REQUEST;
			case NOT_DEAL_BUYER, NOT_DEAL_SELLER -> HttpStatus.FORBIDDEN;
			case DEAL_NOT_FOUND -> HttpStatus.NOT_FOUND;
			case INVALID_DEAL_STATUS -> HttpStatus.CONFLICT;
		};
	}

	private static String messageOf(DealErrorCode errorCode) {
		return switch (errorCode) {
			case UNAUTHORIZED -> "로그인이 필요합니다.";
			case INVALID_SHIPPING_ADDRESS -> "배송지 정보를 다시 확인해 주세요.";
			case INVALID_DEAL_FILTER -> "조회 조건이 올바르지 않습니다.";
			case INVALID_CURSOR -> "커서가 올바르지 않습니다.";
			case NOT_DEAL_BUYER -> "이 거래의 구매자만 할 수 있습니다.";
			case NOT_DEAL_SELLER -> "이 거래의 판매자만 할 수 있습니다.";
			case DEAL_NOT_FOUND -> "거래를 찾을 수 없습니다.";
			case INVALID_DEAL_STATUS -> "지금 단계에서는 처리할 수 없는 요청입니다.";
		};
	}
}
