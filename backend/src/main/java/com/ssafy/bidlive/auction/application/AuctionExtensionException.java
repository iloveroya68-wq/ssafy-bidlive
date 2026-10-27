package com.ssafy.bidlive.auction.application;

import com.ssafy.bidlive.auction.domain.AuctionExtensionErrorCode;

/** 판매자 방송 연장이 계약상 거부된 사유를 Application 경계로 전달한다. */
public class AuctionExtensionException extends RuntimeException {

	private final AuctionExtensionErrorCode reason;

	public AuctionExtensionException(AuctionExtensionErrorCode reason) {
		super(reason.name());
		this.reason = reason;
	}

	public AuctionExtensionErrorCode getReason() {
		return reason;
	}
}
