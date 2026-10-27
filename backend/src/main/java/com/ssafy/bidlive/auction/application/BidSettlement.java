package com.ssafy.bidlive.auction.application;

/** 입찰자 개인 정산. 결과값은 {@code WON}/{@code LOST}이며 예측의 {@code HIT}/{@code MISS}와 섞지 않는다 */
public record BidSettlement(String result, long submittedAmount, Long paymentAmount, long releasedAmount) {

	/** 미낙찰·유찰 — 제출가 전액이 풀리고 결제는 없다 */
	public static BidSettlement released(long submittedAmount) {
		return new BidSettlement("LOST", submittedAmount, null, submittedAmount);
	}
}
