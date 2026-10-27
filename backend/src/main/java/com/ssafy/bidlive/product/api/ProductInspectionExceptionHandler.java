package com.ssafy.bidlive.product.api;

import com.ssafy.bidlive.product.api.dto.ProductErrorResponse;
import com.ssafy.bidlive.product.application.ProductInspectionRejectedException;
import com.ssafy.bidlive.product.domain.ProductInspectionFailureCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** AI-04의 입력·인증·Credit 실패를 명세상 HTTP 오류로 변환합니다. */
@RestControllerAdvice(assignableTypes = ProductInspectionController.class)
public class ProductInspectionExceptionHandler {

	@ExceptionHandler(ProductInspectionRejectedException.class)
	ResponseEntity<ProductErrorResponse> handleRejectedInspection(
		ProductInspectionRejectedException exception
	) {
		ProductInspectionFailureCode failureCode = exception.getFailureCode();
		return ResponseEntity.status(httpStatus(failureCode))
			.body(new ProductErrorResponse(failureCode.name(), message(failureCode)));
	}

	@ExceptionHandler(ProductInspectionUnauthenticatedException.class)
	ResponseEntity<ProductErrorResponse> handleUnauthenticated(
		ProductInspectionUnauthenticatedException exception
	) {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
			.body(new ProductErrorResponse("UNAUTHORIZED", "인증이 필요합니다."));
	}

	/**
	 * 검열 대상이 하나도 없으면 도메인 값 객체가 IllegalArgumentException을 던진다. 형식 검증
	 * 실패와 같은 입력 오류로 다룬다.
	 */
	@ExceptionHandler({
		MethodArgumentNotValidException.class,
		HttpMessageNotReadableException.class,
		IllegalArgumentException.class
	})
	ResponseEntity<ProductErrorResponse> handleInvalidRequest(Exception exception) {
		return ResponseEntity.badRequest()
			.body(new ProductErrorResponse(
				ProductInspectionFailureCode.INVALID_INSPECTION_REQUEST.name(),
				message(ProductInspectionFailureCode.INVALID_INSPECTION_REQUEST)
			));
	}

	private HttpStatus httpStatus(ProductInspectionFailureCode failureCode) {
		return switch (failureCode) {
			case INVALID_INSPECTION_REQUEST -> HttpStatus.BAD_REQUEST;
			case AI_CREDIT_EXHAUSTED -> HttpStatus.BAD_GATEWAY;
		};
	}

	private String message(ProductInspectionFailureCode failureCode) {
		return switch (failureCode) {
			case INVALID_INSPECTION_REQUEST ->
				"검열할 제목·설명·이미지 중 최소 하나가 필요하며 이미지 개수 한도를 넘을 수 없습니다.";
			case AI_CREDIT_EXHAUSTED -> "AI 검열 크레딧이 소진되어 검열을 수행할 수 없습니다.";
		};
	}
}
