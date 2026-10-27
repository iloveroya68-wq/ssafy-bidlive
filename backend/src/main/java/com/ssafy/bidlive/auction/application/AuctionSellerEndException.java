package com.ssafy.bidlive.auction.application;

import com.ssafy.bidlive.auction.domain.AuctionSellerEndErrorCode;

/** 판매자 종료 실패를 오류 코드와 함께 API 계층으로 올린다. */
public class AuctionSellerEndException extends RuntimeException {

	private final AuctionSellerEndErrorCode errorCode;

	public AuctionSellerEndException(AuctionSellerEndErrorCode errorCode) {
		this(errorCode, null);
	}

	public AuctionSellerEndException(
		AuctionSellerEndErrorCode errorCode,
		Throwable cause
	) {
		super(errorCode.name(), cause);
		this.errorCode = errorCode;
	}

	public AuctionSellerEndErrorCode getErrorCode() {
		return errorCode;
	}
}
