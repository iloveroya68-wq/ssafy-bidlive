package com.ssafy.bidlive.report.application;

/**
 * 신고 가능 여부를 판정하는 데 필요한 경매방 사실들이다. 한 번의 조회로 모아 오고 판정은 유스케이스가 한다.
 *
 * <p>{@code status}를 문자열로 두는 이유는 다른 도메인과 같다 — `auction_room.status`에 DB 제약이
 * 없어 예상 밖의 값 하나가 enum 변환 실패로 500을 만든다. 판정에 필요한 것은 "`WAITING`이나
 * `LIVE`인가" 뿐이다.
 */
public record AuctionReportTarget(
	long auctionId,
	Long sellerId,
	String status,
	String visibility,
	boolean deleted
) {
}
