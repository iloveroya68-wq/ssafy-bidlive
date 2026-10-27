package com.ssafy.bidlive.deal.application;

public record MemberDealActivity(
	long salesCount,
	long purchaseCount
) {

	public MemberDealActivity {
		if (salesCount < 0 || purchaseCount < 0) {
			throw new IllegalArgumentException("deal activity counts must not be negative");
		}
	}
}
