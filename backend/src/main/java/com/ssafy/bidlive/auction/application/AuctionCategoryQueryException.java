package com.ssafy.bidlive.auction.application;

import com.ssafy.bidlive.auction.domain.AuctionCategoryErrorCode;

/** 카테고리 목록을 조회할 수 없을 때 API 계층으로 오류 코드를 전달한다. */
public class AuctionCategoryQueryException extends RuntimeException {

	private final AuctionCategoryErrorCode errorCode;

	public AuctionCategoryQueryException(
		AuctionCategoryErrorCode errorCode,
		Throwable cause
	) {
		super(errorCode.name(), cause);
		this.errorCode = errorCode;
	}

	public AuctionCategoryErrorCode getErrorCode() {
		return errorCode;
	}
}
