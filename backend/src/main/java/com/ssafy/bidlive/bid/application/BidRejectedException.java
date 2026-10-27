package com.ssafy.bidlive.bid.application;

import com.ssafy.bidlive.bid.domain.BidFailureCode;

/** 원자 입찰 처리에서 거부된 사유를 API 계층까지 전달한다. */
public class BidRejectedException extends RuntimeException {

	private final BidFailureCode failureCode;

	public BidRejectedException(BidFailureCode failureCode) {
		super(failureCode.name());
		this.failureCode = failureCode;
	}

	public BidRejectedException(
		BidFailureCode failureCode,
		Throwable cause
	) {
		super(failureCode.name(), cause);
		this.failureCode = failureCode;
	}

	public BidFailureCode getFailureCode() {
		return failureCode;
	}
}
