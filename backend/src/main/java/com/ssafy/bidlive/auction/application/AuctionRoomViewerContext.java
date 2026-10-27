package com.ssafy.bidlive.auction.application;

/** 공개 상세 조회를 요청한 선택 인증 회원의 식별자와 관리자 여부입니다. */
public record AuctionRoomViewerContext(
	Long memberId,
	boolean admin
) {

	public static AuctionRoomViewerContext anonymous() {
		return new AuctionRoomViewerContext(null, false);
	}

	public static AuctionRoomViewerContext authenticated(
		long memberId,
		boolean admin
	) {
		if (memberId <= 0) {
			throw new IllegalArgumentException("memberId는 양수여야 합니다.");
		}
		return new AuctionRoomViewerContext(memberId, admin);
	}
}
