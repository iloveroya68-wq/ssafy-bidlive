package com.ssafy.bidlive.product.api;

import com.ssafy.bidlive.product.api.dto.ProductErrorResponse;
import com.ssafy.bidlive.product.application.ProductImageUploadException;
import com.ssafy.bidlive.product.domain.ProductImageUploadErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

/** 상품 이미지 업로드 실패를 API 명세의 오류 코드와 상태로 변환합니다. */
@RestControllerAdvice(assignableTypes = ProductImageController.class)
public class ProductImageExceptionHandler {

	@ExceptionHandler(ProductImageUploadException.class)
	ResponseEntity<ProductErrorResponse> handleUpload(
		ProductImageUploadException exception
	) {
		ProductImageUploadErrorCode errorCode = exception.getErrorCode();
		return ResponseEntity.status(status(errorCode))
			.body(new ProductErrorResponse(errorCode.name(), message(errorCode)));
	}

	@ExceptionHandler(MissingServletRequestPartException.class)
	ResponseEntity<ProductErrorResponse> handleMissingImage(
		MissingServletRequestPartException exception
	) {
		return ResponseEntity.badRequest()
			.body(new ProductErrorResponse(
				ProductImageUploadErrorCode.IMAGE_REQUIRED.name(),
				message(ProductImageUploadErrorCode.IMAGE_REQUIRED)
			));
	}

	@ExceptionHandler(MaxUploadSizeExceededException.class)
	ResponseEntity<ProductErrorResponse> handleMaxUploadSize(
		MaxUploadSizeExceededException exception
	) {
		return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
			.body(new ProductErrorResponse(
				ProductImageUploadErrorCode.IMAGE_SIZE_EXCEEDED.name(),
				message(ProductImageUploadErrorCode.IMAGE_SIZE_EXCEEDED)
			));
	}

	private HttpStatus status(ProductImageUploadErrorCode errorCode) {
		return switch (errorCode) {
			case IMAGE_REQUIRED, UNSUPPORTED_IMAGE_TYPE -> HttpStatus.BAD_REQUEST;
			case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
			case IMAGE_SIZE_EXCEEDED -> HttpStatus.PAYLOAD_TOO_LARGE;
			case IMAGE_STORAGE_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
		};
	}

	private String message(ProductImageUploadErrorCode errorCode) {
		return switch (errorCode) {
			case IMAGE_REQUIRED -> "상품 이미지가 필요합니다.";
			case UNSUPPORTED_IMAGE_TYPE -> "지원하지 않는 이미지 형식입니다.";
			case UNAUTHORIZED -> "인증된 판매자만 상품 이미지를 업로드할 수 있습니다.";
			case IMAGE_SIZE_EXCEEDED -> "상품 이미지 크기가 허용 범위를 초과했습니다.";
			case IMAGE_STORAGE_UNAVAILABLE -> "이미지 저장소를 사용할 수 없습니다.";
		};
	}
}
