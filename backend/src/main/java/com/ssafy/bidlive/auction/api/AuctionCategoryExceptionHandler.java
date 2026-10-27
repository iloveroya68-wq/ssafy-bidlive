package com.ssafy.bidlive.auction.api;

import com.ssafy.bidlive.auction.api.dto.AuctionCategoryErrorResponse;
import com.ssafy.bidlive.auction.application.AuctionCategoryQueryException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 카테고리 목록 조회 실패를 명세의 503 오류 응답으로 변환한다. */
@RestControllerAdvice(assignableTypes = AuctionCategoryController.class)
public class AuctionCategoryExceptionHandler {

	@ExceptionHandler(AuctionCategoryQueryException.class)
	ResponseEntity<AuctionCategoryErrorResponse> handleCategoryQuery(
		AuctionCategoryQueryException exception
	) {
		return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
			.body(new AuctionCategoryErrorResponse(
				exception.getErrorCode().name(),
				"카테고리 목록을 조회할 수 없습니다."
			));
	}
}
