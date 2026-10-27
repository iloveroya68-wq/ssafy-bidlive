package com.ssafy.bidlive.auction.domain;

/** 방송 연장 가능 여부와 거부된 경우의 단일 사유를 표현한다. */
public record AuctionExtensionDecision(
	boolean extendable,
	AuctionExtensionErrorCode failureCode
) {

	public AuctionExtensionDecision {
		if (extendable && failureCode != null) {
			throw new IllegalArgumentException(
				"extendable decision must not have a failure code"
			);
		}
		if (!extendable && failureCode == null) {
			throw new IllegalArgumentException(
				"rejected decision must have a failure code"
			);
		}
	}

	public static AuctionExtensionDecision allowed() {
		return new AuctionExtensionDecision(true, null);
	}

	public static AuctionExtensionDecision rejected(
		AuctionExtensionErrorCode failureCode
	) {
		return new AuctionExtensionDecision(false, failureCode);
	}
}
