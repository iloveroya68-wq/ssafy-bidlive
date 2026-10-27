package com.ssafy.bidlive.auction.application;

/**
 * 결과 헤더 한 행. 방과 {@code auction_result}를 LEFT JOIN한 것이라
 * <b>정산 전에는 {@code resultStatus}가 null</b>이고, 그 자체가 「결과 확정 전」 판정 근거다.
 *
 * <p>{@code snapshotUrl}(LIVE-06)도 같은 LEFT JOIN으로 붙는다 — 판매자가 캡처를 안 올렸으면 null이다.
 */
public record AuctionResultHeaderRow(
	long auctionId,
	long sellerMemberId,
	String resultStatus,
	String terminationReason,
	Long finalPrice,
	Long winnerParticipantId,
	String winnerNickname,
	String snapshotUrl
) {

	public boolean settled() {
		return resultStatus != null;
	}

	public boolean sold() {
		return "SOLD".equals(resultStatus);
	}
}
