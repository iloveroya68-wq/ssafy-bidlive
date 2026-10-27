package com.ssafy.bidlive.bid.api;

import com.ssafy.bidlive.bid.api.dto.BidErrorResponse;
import com.ssafy.bidlive.bid.application.BidRejectedException;
import com.ssafy.bidlive.bid.domain.BidFailureCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** BID-06의 검증·인증·비즈니스 실패를 명세상 HTTP 오류로 변환한다. */
@RestControllerAdvice(assignableTypes = BidController.class)
public class BidExceptionHandler {

	@ExceptionHandler(BidRejectedException.class)
	ResponseEntity<BidErrorResponse> handleRejectedBid(BidRejectedException exception) {
		BidFailureCode failureCode = exception.getFailureCode();
		return ResponseEntity.status(httpStatus(failureCode))
			.body(new BidErrorResponse(failureCode.name(), message(failureCode)));
	}

	@ExceptionHandler({
		MethodArgumentNotValidException.class,
		HttpMessageNotReadableException.class
	})
	ResponseEntity<BidErrorResponse> handleInvalidAmount(Exception exception) {
		return ResponseEntity.badRequest()
			.body(new BidErrorResponse("INVALID_BID_AMOUNT", "입찰 금액 형식 또는 범위가 올바르지 않습니다."));
	}

	@ExceptionHandler(BidUnauthenticatedException.class)
	ResponseEntity<BidErrorResponse> handleUnauthenticated(BidUnauthenticatedException exception) {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
			.body(new BidErrorResponse("UNAUTHORIZED", "인증이 필요합니다."));
	}

	private HttpStatus httpStatus(BidFailureCode failureCode) {
		return switch (failureCode) {
			case SELLER_CANNOT_BID -> HttpStatus.FORBIDDEN;
			case AUCTION_NOT_FOUND -> HttpStatus.NOT_FOUND;
			case INSUFFICIENT_AVAILABLE_BALANCE -> HttpStatus.UNPROCESSABLE_ENTITY;
			case AUCTION_NOT_LIVE,
				AUCTION_ALREADY_ENDED,
				BID_BELOW_RESERVE_PRICE,
				BID_NOT_HIGHER_THAN_PREVIOUS,
				PREDICTION_ALREADY_JOINED,
				IDEMPOTENCY_KEY_REUSED,
				DUPLICATE_REQUEST -> HttpStatus.CONFLICT;
		};
	}

	private String message(BidFailureCode failureCode) {
		return switch (failureCode) {
			case AUCTION_NOT_FOUND -> "경매가 존재하지 않습니다.";
			case AUCTION_NOT_LIVE -> "입찰 가능한 경매 상태가 아닙니다.";
			case AUCTION_ALREADY_ENDED -> "경매가 이미 종료되었습니다.";
			case BID_BELOW_RESERVE_PRICE -> "입찰 금액이 하한가보다 낮습니다.";
			case BID_NOT_HIGHER_THAN_PREVIOUS -> "기존 입찰가보다 높은 금액을 입력해야 합니다.";
			case SELLER_CANNOT_BID -> "판매자는 자신의 경매에 입찰할 수 없습니다.";
			case PREDICTION_ALREADY_JOINED -> "예측에 참여한 경매에는 입찰할 수 없습니다.";
			case INSUFFICIENT_AVAILABLE_BALANCE -> "사용 가능한 거래 포인트가 부족합니다.";
			case IDEMPOTENCY_KEY_REUSED -> "Idempotency-Key가 다른 입찰 요청에 사용되었습니다.";
			case DUPLICATE_REQUEST -> "이미 처리된 입찰 요청입니다.";
		};
	}
}
